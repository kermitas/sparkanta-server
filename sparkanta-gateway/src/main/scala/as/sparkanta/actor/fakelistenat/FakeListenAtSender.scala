package as.sparkanta.actor.fakelistenat

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import scala.net.IdentifiedInetSocketAddress
import as.sparkanta.actor.tcp.serversocket.ServerSocket

class FakeListenAtSender(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import context.dispatcher

  protected val listenAt = new ServerSocket.ListenAt(new IdentifiedInetSocketAddress(0, "192.168.2.25", 8080), 5 * 1000, 60 * 1000)

  override def preStart(): Unit = try {
    context.system.scheduler.schedule(2 seconds, 55 seconds, self, true)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case true                             => amaConfig.broadcaster ! listenAt
    case a: ServerSocket.ListenAtResult   => log.debug(s"Received listen at result $a.")
    case a: ServerSocket.ListeningStarted => log.debug(s"Received listening started $a.")
    case a: ServerSocket.NewConnection    => // do nothing
    case message                          => log.warning(s"Unhandled $message send by ${sender()}")
  }

}
