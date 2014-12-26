package as.sparkanta.actor.fakelistenat

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.message.{ ListenAt, ListenAtSuccessResult, ListenAtErrorResult }
import scala.net.IdentifiedInetSocketAddress

/**
 * This actor is publishing on broadcaster [[ListenAt]] message.
 *
 * It is useful when there is no rest-server but we want to have port open.
 */
class FakeListenAtSender(amaConfig: AmaConfig, config: FakeListenAtSenderConfig) extends Actor with ActorLogging {

  def this(amaConfig: AmaConfig) = this(amaConfig, FakeListenAtSenderConfig.fromTopKey(amaConfig.config))

  protected val listenAddress = new IdentifiedInetSocketAddress(config.listenId, config.listenIp, config.listenPort)
  protected val forwardToRestAddress = new IdentifiedInetSocketAddress(config.forwardToRestId, config.forwardToRestIp, config.forwardToRestPort)

  protected val listenAt = new ListenAt(
    listenAddress,
    config.openingServerSocketTimeoutInSeconds,
    config.keepServerSocketOpenTimeoutInSeconds,
    forwardToRestAddress
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
      log.debug(s"Publishing ${classOf[ListenAt].getSimpleName} to listen at $listenAddress and forward to REST $forwardToRestAddress.")
      amaConfig.broadcaster ! listenAt
    }

    case lasr: ListenAtSuccessResult => log.warning(s"Received ${lasr.getClass.getSimpleName}.")

    case laer: ListenAtErrorResult   => log.error(laer.exception.get, s"Received ${laer.getClass.getSimpleName}.")

    case message                     => log.warning(s"Unhandled $message send by ${sender()}")
  }
}