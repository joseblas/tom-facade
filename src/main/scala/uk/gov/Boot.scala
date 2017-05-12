package uk.gov

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

import scala.sys.process._
import scala.util.{Failure, Success, Try}


object Boot extends App with MultipartFormDataHandler {

  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()


  val logger = Logging(system, getClass)

  val cmd = config.getString("tom.pythonCmd") + " -V"
  Try((cmd !!)) match {
    case Failure(s) =>
      logger.error(s"Python is not installed here: ${config.getString("tom.pythonCmd")}")
      sys.exit(1)
    case Success(info) => logger.info(s" Python succesfully installed ${config.getString("tom.pythonCmd")} ${info}")
  }

  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port")) map { result =>
    logger.debug(s"Server has started on port ${config.getInt("http.port")}")
  } recover {
    case ex: Exception => println(s"Server binding failed due to ${ex.getMessage}")
  }
}