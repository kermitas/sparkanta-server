package as.sparkanta.actor.device

import akka.actor.{ ActorRef, ActorLogging, Actor, Props, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.{ Device => DeviceSpec }

class Device(amaConfig: AmaConfig) extends Actor with ActorLogging {

  override val supervisorStrategy = OneForOneStrategy() {
    case _ => SupervisorStrategy.Stop
  }

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new DeviceClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: DeviceSpec.Start => start(a, sender)
    case message             => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def start(start: DeviceSpec.Start, startSender: ActorRef): Unit = try {
    val props = Props(new DeviceWorker(start, startSender, amaConfig.broadcaster, self))
    context.actorOf(props, classOf[DeviceWorker].getSimpleName + "-" + start.connectionInfo.remote.id)
  } catch {
    case e: Exception => {
      val startErrorResult = new DeviceSpec.StartErrorResult(e, start, startSender)
      startErrorResult.reply(self)
    }
  }
}