package as.sparkanta.ama.actor.tcp.falelistenat

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.server.message.{ ListenAt, ListenAtSuccessResult, ListenAtErrorResult }

object FakeListenAtSender {
  lazy final val listenAt = new ListenAt("localhost", 8080, 5, 30, "localhost", 8085)
}

//TODO: until there is no reall rest-server then this actor is faking by publishing on broadcaster ListenAt message
class FakeListenAtSender(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import FakeListenAtSender._

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      import context.dispatcher
      context.system.scheduler.schedule(2 seconds, 20 seconds, amaConfig.broadcaster, listenAt)

      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {

    case lasr: ListenAtSuccessResult => log.warning(s"Received ${lasr.getClass.getSimpleName}.")

    case laer: ListenAtErrorResult   => log.error(laer.exception.get, s"Received ${laer.getClass.getSimpleName}.")

    case message                     => log.warning(s"Unhandled $message send by ${sender()}")
  }
}