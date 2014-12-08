package as.sparkanta.ama.actor.restforwarder

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.message.ForwardToRestServer
import as.akka.broadcaster.Broadcaster

class RestForwarder(amaConfig: AmaConfig) extends Actor with ActorLogging {

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new RestForwarderClassifier)

      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
    }
  }

  override def receive = {
    case ftrs: ForwardToRestServer => forwardToRestServer(ftrs)
    case message                   => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def forwardToRestServer(ftrs: ForwardToRestServer): Unit = {
    // TODO: connect, perform REST PUT (with ftrs serialized to JSON), read response, close connection
  }
}
