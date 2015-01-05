package as.sparkanta.actor.device1

import akka.actor.{ ActorRef, ActorLogging, Actor, Props, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.{ Device => DeviceSpec }
import scala.collection.mutable.Map

class Device(amaConfig: AmaConfig) extends Actor with ActorLogging {

  protected val map = Map[Long, ActorRef]() // remote address id -> device worker

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
    case a: DeviceSpec.Start   => start(a, sender)

    case a: DeviceSpec.Started => deviceStarted(a, sender) // TODO remember to send it directly from worker to deviceActor (parent)

    case a: DeviceSpec.Stopped => deviceStopped(a) // TODO remember to send it directly from worker to deviceActor (parent)

    case message               => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def start(start: DeviceSpec.Start, startSender: ActorRef): Unit = try {
    if (map.contains(start.connectionInfo.remote.id)) {
      throw new Exception(s"Id ${start.connectionInfo.remote.id} is already registered, could not register new.")
    } else {
      val props = Props(new DeviceWorker(start, startSender, amaConfig.broadcaster, self))
      context.actorOf(props, classOf[DeviceWorker].getSimpleName + "-" + start.connectionInfo.remote.id)
    }
  } catch {
    case e: Exception => {
      val startErrorResult = new DeviceSpec.StartErrorResult(e, start, startSender)
      startErrorResult.reply(self)
    }
  }

  protected def deviceStarted(deviceStartedMessage: DeviceSpec.Started, deviceWorker: ActorRef): Unit =
    deviceStarted(deviceStartedMessage.request1.message.connectionInfo.remote.id, deviceWorker)

  protected def deviceStarted(id: Long, deviceWorker: ActorRef): Unit = map.get(id) match {

    case Some(deviceWorker) => {
      val exception = new Exception(s"Remote address id $id is already known (served by worker actor $deviceWorker), could not add it again.")
      amaConfig.broadcaster ! new DeviceSpec.DisconnectDevice(id, exception)
    }

    case None => {
      map.put(id, deviceWorker)
      log.debug(s"Remote address id $id was added (worker actor $deviceWorker), currently there are ${map.size} opened sockets (ids: ${map.keySet.mkString(",")}).")
    }
  }

  protected def deviceStopped(deviceStoppedMessage: DeviceSpec.Stopped): Unit =
    deviceStopped(deviceStoppedMessage.request1.message.connectionInfo.remote.id)

  protected def deviceStopped(id: Long): Unit = map.remove(id).map { deviceWorker =>
    log.debug(s"Remote address id $id was removed (worker actor $deviceWorker), currently there are ${map.size} opened sockets (ids: ${map.keySet.mkString(",")}).")
  }
}
