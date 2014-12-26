/*
package as.sparkanta.actor.restforwarder

import akka.actor.{ ActorLogging, Actor }
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.message.{ ForwardToRestServer, MessageFromDevice }
import as.akka.broadcaster.Broadcaster
import scala.net.IdentifiedInetSocketAddress

class RestForwarder(amaConfig: AmaConfig, localAddress: IdentifiedInetSocketAddress, restAddress: IdentifiedInetSocketAddress) extends Actor with ActorLogging {

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    // notifying broadcaster to register us with given classifier
    amaConfig.broadcaster ! new Broadcaster.Register(self, new RestForwarderClassifier(localAddress))
  }

  override def receive = {
    case ftrs: ForwardToRestServer => forwardToRestServer(ftrs)
    case message                   => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def forwardToRestServer(ftrs: ForwardToRestServer): Unit = try {
    log.debug(s"Will try to forward message $ftrs to REST server at $restAddress.")

    // TODO: connect (can connection made by spray be long lived?), perform REST PUT (with ftrs serialized to JSON), read response, close connection
    // TODO: if response then ok, if not then throw exception

    log.debug(s"Forwarding message $ftrs to REST server at $restAddress was successful.")
  } catch {
    case e: Exception => {
      log.error(e, s"Forwarding message $ftrs to REST server at $restAddress failed.")
      throw e
    }
  }
}
*/ 