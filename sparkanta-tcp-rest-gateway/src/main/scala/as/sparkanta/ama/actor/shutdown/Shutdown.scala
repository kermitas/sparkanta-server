package as.sparkanta.ama.actor.shutdown

import akka.actor.{ Actor, ActorLogging }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.inputstream.InputStreamText
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.ama.config.AmaConfig

class Shutdown(amaConfig: AmaConfig) extends Actor with ActorLogging {

  /**
   * Will be executed when actor is created and also after actor restart (if postRestart() is not override).
   */
  override def preStart(): Unit = {
    try {
      // notifying broadcaster to register us with given classifier
      amaConfig.broadcaster ! new Broadcaster.Register(self, new ShutdownClassifier)

      // remember always to send back how your initialization goes
      amaConfig.sendInitializationResult()
    } catch {
      case e: Exception => amaConfig.sendInitializationResult(new Exception("Problem while installing sample actor.", e))
    }
  }

  override def receive = {
    // received text from console
    case InputStreamText(inputText) => {
      log.info(s"Input text (${inputText.length} characters):$inputText")

      if (inputText.isEmpty) {
        log.info("Empty input string means that we will finish!")
        amaConfig.broadcaster ! new ShutdownSystem(Right("[enter] was pressed in console"))
        context.stop(self)
      }
    }

    case message => log.warning(s"Unhandled $message send by ${sender()}")
  }
}