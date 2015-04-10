package demo

import java.util.concurrent.TimeoutException

import akka.actor.{Props, ActorSystem}

object Server {

  def main(args: Array[String]) {

    val system = ActorSystem("server")
    val serverActor = system.actorOf(Props(classOf[ServerActor]), name = "server")


    //system.shutdown()
  }

}