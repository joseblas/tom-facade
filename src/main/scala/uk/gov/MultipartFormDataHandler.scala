package uk.gov


import java.io.{File, PrintWriter}

import akka.actor.ActorSystem
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.BodyPart
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import spray.json.DefaultJsonProtocol

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.sys.process._

case class NlpRequest(filename: String, content: String)

case class ResultRow(entity: String, occurs: Option[Long])

case class Result(filename: String, timestamp: Option[Long], results: List[ResultRow])

//object Result{
//  def apply(filename: String, results: List[ResultRow]): Result = Result(filename, None, results)
//}

trait Protocols extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val resultRow = jsonFormat2(ResultRow.apply)
  implicit val result = jsonFormat3(Result.apply)
  implicit val nlpRequest = jsonFormat2(NlpRequest.apply)
}

trait MultipartFormDataHandler extends Protocols {

  implicit val system: ActorSystem

  implicit def executor: ExecutionContextExecutor

  implicit val materializer: Materializer

  val routes = processMultiPartData

  def processMultiPartData: Route = path("upload") {
    (post & entity(as[NlpRequest])) { req =>
      complete {
        val tmpFile = File.createTempFile("nlp", ".txt")

        val pw = new PrintWriter(tmpFile)
        try
          pw.write(req.content)
        finally
          pw.close()

        val cmd = s"python3 /Users/jta/bdec/tom/tom.py ${tmpFile.getAbsolutePath}"
        val resultr = (cmd !!)
        val r: List[ResultRow] = resultr.split(System.lineSeparator()).toList.map(row => ResultRow(row.split(",")(0), Option(row.split(",")(1).trim.toLong)))
        Result(req.filename, Some(System.currentTimeMillis()), r)
      }

    } ~
      (post & entity(as[Multipart.FormData])) { formData =>
        complete {
          val extractedData: Future[Map[String, Any]] = formData.parts.mapAsync[(String, Any)](1) {

            case file: BodyPart if file.name == "file" =>
              val tempFile = File.createTempFile("nlp", ".txt")
              println(s"file ${tempFile.getAbsolutePath}")
              file.entity.dataBytes.runWith(FileIO.toPath(tempFile.toPath)).map { ioResult =>
                //              s"file ${file.filename.getOrElse("Unknown")}" -> s"${ioResult.count} bytes"
                "path" -> tempFile.getAbsolutePath
              }

            //          case data: BodyPart => data.toStrict(2.seconds).map(strict => data.name -> strict.entity.data.utf8String)
          }.runFold(Map.empty[String, Any])((map, tuple) => map + tuple)

          extractedData.map[ToResponseMarshallable] { data =>
            data.get("path") match {
              case Some(url) =>
                val cmd = "python3 /Users/jta/bdec/tom/tom.py " + url
                val resultr = (cmd !!)
                val r: List[ResultRow] = resultr.split(System.lineSeparator()).toList.map(row => ResultRow(row.split(",")(0), Option(row.split(",")(1).trim.toLong)))
                //              println(r)
                //              r
                Result("test", Some(System.currentTimeMillis()), r)


            }
          }
        }
      }
  }
}
