package as.sparkanta.ama.actor.restforwarder

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.message.ForwardToRestServer
import as.akka.broadcaster.Broadcaster

class RestForwarder(amaConfig: AmaConfig, localIp: String, localPort: Int, restIp: String, restPort: Int) extends Actor with ActorLogging {

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new RestForwarderClassifier(localIp, localPort))
  }

  override def receive = {
    case ftrs: ForwardToRestServer => forwardToRestServer(ftrs)
    case message                   => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def forwardToRestServer(ftrs: ForwardToRestServer): Unit = try {
    log.debug(s"Will try to forward ${ftrs.getClass.getSimpleName} to REST server at $restIp:$restPort.")

    // TODO: connect (can connection made by spray be long lived?), perform REST PUT (with ftrs serialized to JSON), read response, close connection
    // TODO: if response then ok, if not then throw exception

    log.debug(s"Forwarding ${ftrs.getClass.getSimpleName} to REST server at $restIp:$restPort was successful.")
  } catch {
    case e: Exception => {
      log.error(e, s"Forwarding ${ftrs.getClass.getSimpleName} to REST server at $restIp:$restPort failed.")
      throw e
    }
  }
}
