package demo

import akka.pattern._
import akka.actor.{Cancellable, ActorRef, Actor}
import akka.util.Timeout
import com.persist.JsonOps._
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTimeZone, DateTime}
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.{Await, Promise}

class ServerActor() extends Actor {

  private[this] implicit val executionContext = context.dispatcher

  private val dtz = DateTimeZone.forID("US/Pacific")
  private val formatter = DateTimeFormat.forPattern("HH:mm:ss.SS")

  private[this] var listeners = Map.empty[Long, Cancellable]

  private def delay(seconds: Int): Unit = {
    println("Working...")
    Thread.sleep(1000 * seconds)
  }

  private def respond(sender: ActorRef, j: Json): Unit = {
    sender ! Compact(j)
  }

  private def progress(sender: ActorRef, id: Long, msg: String): Unit = {
    respond(sender, JsonObject("cmd" -> "progress", "msg" -> msg, "id" -> id))
  }


  private def act(msg: String, id: Long, canFail: Boolean = true): JsonObject = {
    delay(10)
    if (canFail && msg.contains("fail")) throw new Exception(s"failed on: $msg")
    JsonObject("cmd" -> "done", "id" -> id, "msg" -> s"Processed: $msg")
  }

  private def actProgress(sender: ActorRef, msg: String, id: Long): JsonObject = {
    delay(1)
    progress(sender, id, "Finished step1")
    delay(2)
    progress(sender, id, "Finished step2")
    delay(5)
    progress(sender, id, "Finished step3")
    delay(3)
    if (msg.contains("fail")) throw new Exception(s"failed on: $msg")
    JsonObject("cmd" -> "done", "id" -> id, "msg" -> s"Processed: $msg")
  }

  private def askClient(sender: ActorRef, id: Long, name: String, vals: Seq[String]): String = {
    implicit val timeout: Timeout = Timeout(2 minutes)
    val sender1 = sender
    val info = Compact(JsonObject("cmd" -> "query", "name" -> name, "vals" -> vals, "id" -> id))
    val userSelectionF = sender1 ? info
    val userSelection = try {
      Await.result(userSelectionF, 1 minute)
    } catch {
      case ex: Exception => "???"
    }
    jgetString(userSelection)
  }

  private def askQuery(sender: ActorRef, msg: String, id: Long): JsonObject = {
    val color =
      askClient(sender, id, "color", Seq("red", "green", "blue"))
    val direction =
      askClient(sender, id, "direction", Seq("north", "south", "east", "west"))
    JsonObject("cmd" -> "done", "id" -> id, "msg" -> s"Processed: $msg $color:$direction")
  }

  override def receive: Receive = {

    case s: String =>
      val j = jgetObject(Json(s))
      val cmd = jgetString(j, "cmd")
      val id = jgetLong(j, "id")
      val msg = jgetString(j, "msg")
      cmd match {

        case "OneWay" =>
          println(s"OneWay: $msg")

        case "RequestResponse" =>
          respond(sender, act(msg, id, canFail = false))

        case "RequestResponseFail" =>
          try {
            respond(sender, act(msg, id))
          } catch {
            case ex: Exception =>
              sender ! Compact(JsonObject("cmd" -> "fail", "id" -> id, "msg" -> ex.getMessage))
          }

        case "RequestResponseAck" =>
          sender ! Compact(JsonObject("cmd" -> "ack", "id" -> id))
          try {
            respond(sender, act(msg, id))
          } catch {
            case ex: Exception =>
              sender ! Compact(JsonObject("cmd" -> "fail", "id" -> id, "msg" -> ex.getMessage))
          }

        case "RequestResponseProgress" =>
          sender ! Compact(JsonObject("cmd" -> "ack", "id" -> id))
          try {
            respond(sender, actProgress(sender, msg, id))
          } catch {
            case ex: Exception =>
              sender ! Compact(JsonObject("cmd" -> "fail", "id" -> id, "msg" -> ex.getMessage))
          }

        case "RequestResponseQuery" =>
          sender ! Compact(JsonObject("cmd" -> "ack", "id" -> id))
          try {
            respond(sender, askQuery(sender, msg, id))
          } catch {
            case ex: Exception =>
              sender ! Compact(JsonObject("cmd" -> "fail", "id" -> id, "msg" -> ex.getMessage))
          }

        case "StartListen" =>
          val sender1 = sender
          val t = context.system.scheduler.schedule(1 seconds, 3 seconds) {
            val now = DateTime.now(dtz)
            val msg = JsonObject("cmd" -> "listen", "msg" -> formatter.print(now), "id"->id)
            sender1 ! Compact(msg)
          }
          listeners += id -> t

        case "StopListen" =>
          val lid = jgetString(j, "lid")
          listeners.get(id) match {
            case Some(t) =>
              t.cancel()
              listeners -= id
            case None =>
          }
      }
  }
}
