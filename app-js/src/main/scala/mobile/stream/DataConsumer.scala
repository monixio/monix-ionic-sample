package mobile.stream

import monix.execution.Cancelable
import monix.reactive.Observable
import monix.reactive.OverflowStrategy.DropNew
import monix.reactive.observers.Subscriber
// import org.scalajs.dom
import shared.models._
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js.Dynamic.global

final class DataConsumer(interval: FiniteDuration, seed: Long, doBackPressure: Boolean)
  extends Observable[Event] {

  def unsafeSubscribeFn(subscriber: Subscriber[Event]): Cancelable = {
    val hostEmulator = "10.0.2.2:9000"
    val hostBrowser = "localhost:9000"
    val hostHeroku = "monifu-ionic-sample.herokuapp.com"
    val host = hostBrowser

    val source = if (doBackPressure) {
      val url = s"ws://$host/back-pressured-stream?periodMillis=${interval.toMillis}&seed=$seed"
      BackPressuredWebSocketClient(url)
    }
    else {
      val url = s"ws://$host/simple-stream?periodMillis=${interval.toMillis}&seed=$seed"
      SimpleWebSocketClient(url, DropNew(1000))
    }

    source
      .collect { case IsEvent(e) => e }
      .unsafeSubscribeFn(subscriber)
  }

  object IsEvent {
    def unapply(message: String) = {
      val json = global.JSON.parse(message)
      json.event.asInstanceOf[String] match {
        case "chat" => {
          Some(Signal(
            value = Chat(
              json.value.id.asInstanceOf[Number].intValue(),
              json.value.name.asInstanceOf[String],
              json.value.lastText.asInstanceOf[String],
              json.value.face.asInstanceOf[String]
            ),
            timestamp = json.timestamp.asInstanceOf[Number].longValue()
          ))}
        case "overflow" =>
          Some(OverflowEvent(
            dropped = json.dropped.asInstanceOf[Number].longValue(),
            timestamp = json.timestamp.asInstanceOf[Number].longValue()
          ))
        case "error" =>
          val errorType = json.`type`.asInstanceOf[String]
          val message = json.message.asInstanceOf[String]
          throw new BackPressuredWebSocketClient.Exception(
            s"Server-side error throw - $errorType: $message")
        case _ =>
          None
      }
    }
  }
}
