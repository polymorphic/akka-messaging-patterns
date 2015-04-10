package demo

import java.util
import java.util.concurrent.TimeoutException

import akka.actor.{Props, ActorSystem}
import jline.console.ConsoleReader
import jline.console.completer.{AggregateCompleter, StringsCompleter, Completer}
import ClientActor._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.collection.JavaConversions._

import scala.concurrent.{Await, Promise}

object Client {

  private[this] var lid: Long = 0

  def clearCompleters(reader: ConsoleReader) {
    var old: Completer = null
    reader.getCompleters foreach {
      case completer =>
        old = completer
    }
    if (old != null) reader.removeCompleter(old)
  }

  def mainCompleters(reader: ConsoleReader) {
    reader.setPrompt("client> ")
    clearCompleters(reader)
    val completers = new java.util.LinkedList[Completer]()
    completers.add(new StringsCompleter("exit"))
    completers.add(new StringsCompleter("oneWay"))
    completers.add(new StringsCompleter("requestResponse"))
    completers.add(new StringsCompleter("requestResponseFail"))
    completers.add(new StringsCompleter("requestResponseAck"))
    completers.add(new StringsCompleter("requestResponseProgress"))
    completers.add(new StringsCompleter("requestResponseQuery"))
    completers.add(new StringsCompleter("startListen"))
    completers.add(new StringsCompleter("stopListen"))

    reader.addCompleter(new AggregateCompleter(completers))
  }

  private def setQueryCompleters(reader: ConsoleReader, vals: Seq[String]) {
    clearCompleters(reader)
    var completers = new util.LinkedList[Completer]()
    for (s <- vals) {
      completers.add(new StringsCompleter(s))
    }
    reader.addCompleter(new AggregateCompleter(completers))
  }

  def doQuery(reader: ConsoleReader)(name: String, vals: Seq[String]): String = {
    setQueryCompleters(reader, vals)
    reader.setPrompt(s"${
      Console.CYAN
    }   * ??? $name = ${
      Console.RESET
    }")
    var arg = ""
    while (arg == "") {
      val line = reader.readLine()
      val args = line.split("\\s+")
      if (args.size > 0) arg = args(0)
    }
    mainCompleters(reader)
    arg
  }


  def main(args: Array[String]) {

    val system = ActorSystem("client")
    val clientActor = system.actorOf(Props(classOf[ClientActor]), name = "client")

    val reader = new ConsoleReader()
    mainCompleters(reader)
    try {
      while (true) {
        val line = reader.readLine()
        val args = line.split("\\s+")
        val option = if (args.size >= 2) args(1) else "???"
        try {
          args(0) match {

            case "exit" => throw new Exception("done")

            case "oneWay" => clientActor ! OneWay("test 1 way")

            case "requestResponse" =>
              val p = Promise[String]()
              clientActor ! RequestResponse("test request-response", p)
              val s = Await.result(p.future, 30 second)
              println(s"${Console.GREEN}OK: $s${Console.RESET}")

            case "requestResponseFail" =>
              val p = Promise[String]()
              clientActor ! RequestResponseFail(option, p)
              try {
                val s = Await.result(p.future, 30 second)
                println(s"${Console.GREEN}OK: $s${Console.RESET}")
              } catch {
                case ex: Exception =>
                  println(s"${Console.RED}FAIL: ${ex.getMessage}${Console.RESET}")
              }

            case "requestResponseAck" =>
              val p = Promise[String]()
              clientActor ! RequestResponseAck(option, p)
              try {
                val s = Await.result(p.future, 30 second)
                println(s"${Console.GREEN}OK: $s${Console.RESET}")
              } catch {
                case ex: Exception =>
                  println(s"${Console.RED}FAIL: ${ex.getMessage}${Console.RESET}")
              }

            case "requestResponseProgress" =>
              val p = Promise[String]()
              clientActor ! RequestResponseProgress(option, p)
              try {
                val s = Await.result(p.future, 30 second)
                println(s"${Console.GREEN}OK: $s${Console.RESET}")
              } catch {
                case ex: Exception =>
                  println(s"${Console.RED}FAIL: ${ex.getMessage}${Console.RESET}")
              }

            case "requestResponseQuery" =>
              val p = Promise[String]()
              clientActor ! RequestResponseQuery(option, doQuery(reader), p)
              try {
                val s = Await.result(p.future, 30 second)
                println(s"${Console.GREEN}OK: $s${Console.RESET}")
              } catch {
                case ex: Exception =>
                  println(s"${Console.RED}FAIL: ${ex.getMessage}${Console.RESET}")
              }

            case "startListen" =>
              val p = Promise[Long]
              clientActor ! StartListen(p)
              lid = try {
                Await.result(p.future, 30 seconds)
              } catch {
                case ex: Exception => 0
              }

            case "stopListen" =>
              if (lid != 0)
                clientActor ! StopListen(lid)
              lid = 0

          }
        } catch {
          case ex: TimeoutException =>
            println("Timed Out")
        }
      }
    } catch {
      case ex: Exception =>
    }

    system.shutdown()
  }

}