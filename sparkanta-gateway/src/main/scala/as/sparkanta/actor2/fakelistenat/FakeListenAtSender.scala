package as.sparkanta.actor2.fakelistenat

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import scala.net.IdentifiedInetSocketAddress
import as.sparkanta.actor2.tcp.serversocket.ServerSocket

class FakeListenAtSender(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import context.dispatcher

  protected val listenAt = new ServerSocket.ListenAt(new IdentifiedInetSocketAddress(0, "localhost", 8080), 5 * 1000, 60 * 1000)

  override def preStart(): Unit = try {
    context.system.scheduler.schedule(2 seconds, 55 seconds, self, true)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case true    => amaConfig.broadcaster ! listenAt
    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }

}
