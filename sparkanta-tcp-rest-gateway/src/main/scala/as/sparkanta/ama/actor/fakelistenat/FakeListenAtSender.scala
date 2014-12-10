package as.sparkanta.ama.actor.fakelistenat

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.server.message.{ ListenAt, ListenAtSuccessResult, ListenAtErrorResult }

/**
 * This actor is publishing on broadcaster [[ListenAt]] message.
 *
 * It is useful when there is no rest-server but we want to have port open.
 */
class FakeListenAtSender(amaConfig: AmaConfig, config: FakeListenAtSenderConfig) extends Actor with ActorLogging {

  def this(amaConfig: AmaConfig) = this(amaConfig, FakeListenAtSenderConfig.fromTopKey(amaConfig.config))

  protected val listenAt = new ListenAt(
    config.listenIp,
    config.listenPort,
    config.openingServerSocketTimeoutInSeconds,
    config.keepServerSocketOpenTimeoutInSeconds,
    config.forwardToRestIp,
    config.forwardToRestPort
  )

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      import context.dispatcher
      context.system.scheduler.schedule(2 seconds, config.resendListenAtIntervalInSeconds seconds, self, true)

      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {

    case true => {
      log.debug(s"Publishing ${classOf[ListenAt].getSimpleName} to listen at ${config.listenIp}:${config.listenPort} and forward to REST ${config.forwardToRestIp}:${config.forwardToRestPort}.")
      amaConfig.broadcaster ! listenAt
    }

    case lasr: ListenAtSuccessResult => log.warning(s"Received ${lasr.getClass.getSimpleName}.")

    case laer: ListenAtErrorResult   => log.error(laer.exception.get, s"Received ${laer.getClass.getSimpleName}.")

    case message                     => log.warning(s"Unhandled $message send by ${sender()}")
  }
}