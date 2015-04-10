package demo

import akka.actor.Actor
import com.persist.JsonOps._
import scala.concurrent.duration._
import scala.language.postfixOps

import scala.concurrent.Promise

object ClientActor {

  type qf = (String, Seq[String]) => String

  case class RequestInfo(result: Promise[String], q: Option[qf] = None)

  case class OneWay(msg: String)

  case class RequestResponse(msg: String, result: Promise[String])

  case class RequestResponseFail(msg: String, result: Promise[String])

  case class RequestResponseAck(msg: String, result: Promise[String])

  case class RequestResponseProgress(msg: String, result: Promise[String])

  case class RequestResponseQuery(msg: String, query: (String, Seq[String]) => String, result: Promise[String])

  case class StartListen(result: Promise[Long])

  case class StopListen(id: Long)

  case class AckTimeOut(id: Long)

}

class ClientActor() extends Actor {

  private[this] implicit val executionContext = context.dispatcher

  import ClientActor._

  private[this] val server = context.actorSelection(s"akka.tcp://server@127.0.0.1:9001/user/server")

  private[this] var idCounter: Long = 0
  private[this] var active = Map.empty[Long, RequestInfo]

  def newId() = {
    idCounter += 1
    idCounter
  }

  def send(j: Json): Unit = {
    server ! Compact(j)
  }


  override def receive: Receive = {

    case OneWay(msg) =>
      send(JsonObject("cmd" -> "OneWay", "msg" -> msg))

    case RequestResponse(msg, result) =>
      val id = newId()
      active += id -> RequestInfo(result)
      send(JsonObject("cmd" -> "RequestResponse", "msg" -> msg, "id" -> id))

    case RequestResponseFail(msg, result) =>
      val id = newId()
      active += id -> RequestInfo(result)
      send(JsonObject("cmd" -> "RequestResponseFail", "msg" -> msg, "id" -> id))

    case RequestResponseAck(msg, result) =>
      val id = newId()
      active += id -> RequestInfo(result)
      send(JsonObject("cmd" -> "RequestResponseAck", "msg" -> msg, "id" -> id))
      context.system.scheduler.scheduleOnce(2 seconds) {
        self ! AckTimeOut(id)
      }

    case AckTimeOut(id) =>
      active.get(id) match {
        case Some(request) =>
          val result = request.result
          active -= id
          result.tryFailure(new Exception("server not available"))
        case None =>
      }

    case RequestResponseProgress(msg, result) =>
      val id = newId()
      active += id -> RequestInfo(result)
      send(JsonObject("cmd" -> "RequestResponseProgress", "msg" -> msg, "id" -> id))

    case RequestResponseQuery(msg, q, result) =>
      val id = newId()
      active += id -> RequestInfo(result, Some(q))
      send(JsonObject("cmd" -> "RequestResponseQuery", "msg" -> msg, "id" -> id))

    case StartListen(result) =>
      val id = newId()
      active += id -> null
      send(JsonObject("cmd" -> "StartListen", "id" -> id))
      result.trySuccess(id)

    case StopListen(id) =>
      send(JsonObject("cmd" -> "StopListen", "id" -> id))
      active -= id

    // Responses from server
    case s: String =>
      val j = jgetObject(Json(s))
      val cmd = jgetString(j, "cmd")
      val id = jgetLong(j, "id")

      val msg = jgetString(j, "msg")

      active.get(id) match {
        case Some(request) =>
          cmd match {

            case "done" =>
              val result = request.result
              active -= id
              result.trySuccess(msg)

            case "fail" =>
              val result = request.result
              active -= id
              result.tryFailure(new Exception(msg))

            case "ack" =>

            case "query" =>
              request.q match {
                case Some(q1) =>
                  val name = jgetString(j, "name")
                  val vals = jgetArray(j, "vals").map(jgetString(_))
                  val answer = q1(name, vals)
                  sender ! jgetString(answer)
                case None =>
              }

            case "progress" =>
              println(s"${Console.BLUE}   Progress: $msg${Console.RESET}")

            case "listen" =>
              println(s"${Console.CYAN}   Listen: $msg${Console.RESET}")

          }
        case None =>
      }
  }
}
