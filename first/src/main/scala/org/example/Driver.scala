package org.example

import akka.actor._
import com.typesafe.config.ConfigFactory
import org.example.SharedMessages._
import scopt.OptionParser
import scala.concurrent.duration._

case class Config(configName: String="application.conf", passiveMode: Boolean=false)

object Driver {

  def run(config: Config): Unit = {
    val system = ActorSystem("demo", ConfigFactory.parseResources(config.configName))
    if (!config.passiveMode) {
      val a1 = system.actorOf(Props(classOf[SampleActor]), "actor1")
      val a2 = system.actorOf(Props(classOf[SampleActor]), "actor2")
      a1 ! RequestPing(a2)
    }
    system.awaitTermination(10.seconds)
  }

  def main(args: Array[String]) {
    println("starting up")

    val parser = new OptionParser[Config]("driver") {
      opt[Unit]("passive") action { (_, c) =>
        c.copy(passiveMode = true) } text("passive mode (others create actors)")
      opt[String]("config") action { (x, c) =>
        c.copy(configName = x)} text("akka configuration file")
    }
    parser.parse(args, Config()) match {
      case Some(config) => run(config)
      case None => // display error message
    }

  }
}

object SharedMessages {
  sealed trait DemoMessages
  case object Ping extends DemoMessages
  case object Pong extends DemoMessages
  case class RequestPing(other: ActorRef) extends DemoMessages
}

class SampleActor extends Actor with ActorLogging {
  def receive: Receive = {
    case Ping =>
      log.debug(s"$self received Ping, sending Pong")
      sender() ! Pong
    case Pong =>
      log.debug(s"$self received Pong")
    case RequestPing(other) =>
      log.debug(s"$self sending Ping")
      other ! Ping
  }
}
