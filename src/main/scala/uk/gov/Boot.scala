package uk.gov

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory


object Boot extends App with MultipartFormDataHandler {

  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  val config = ConfigFactory.load()
  val logger = Logging(system, getClass)


  Http().bindAndHandle(routes, config.getString("http.interface"), config.getInt("http.port")) map { result =>
    logger.debug("Server has started on port 9000")
  } recover {
    case ex: Exception => println(s"Server binding failed due to ${ex.getMessage}")
  }
}