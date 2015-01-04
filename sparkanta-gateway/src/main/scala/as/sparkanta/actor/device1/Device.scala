package as.sparkanta.actor.device1

import akka.actor.{ ActorRef, ActorLogging, Actor, Props, OneForOneStrategy, SupervisorStrategy }
import as.akka.broadcaster.Broadcaster
import as.ama.addon.lifecycle.ShutdownSystem
import as.sparkanta.ama.config.AmaConfig
import as.sparkanta.gateway.{ Device => DeviceSpec }
import as.sparkanta.actor.inactivity.InactivityMonitor
import scala.collection.mutable.Map

object Device {
  lazy final val warningTimeAfterMs = 2 * 1000
  lazy final val inactivityTimeAfterMs = 3 * 1000
}

class Device(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import Device._

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
    case a: DeviceSpec.Start         => start(a, sender)

    case a: DeviceSpec.DeviceStarted => deviceStarted(a, sender) // TODO remember to send it directly from worker to deviceActor (parent)

    case a: DeviceSpec.DeviceStopped => deviceStopped(a) // TODO remember to send it directly from worker to deviceActor (parent)

    case message                     => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def start(start: DeviceSpec.Start, startSender: ActorRef): Unit = try {
    val props = Props(new DeviceWorker(start, startSender, amaConfig.broadcaster, self))
    val deviceWorker = context.actorOf(props, classOf[DeviceWorker].getSimpleName + "-" + start.connectionInfo.remote.id)
  } catch {
    case e: Exception => {
      val startErrorResult = new DeviceSpec.StartErrorResult(e, start, startSender)
      startErrorResult.reply(self)
    }
  }

  protected def deviceStarted(deviceStartedMessage: DeviceSpec.DeviceStarted, deviceWorker: ActorRef): Unit =
    deviceStarted(deviceStartedMessage.request1.message.connectionInfo.remote.id, deviceWorker)

  protected def deviceStarted(id: Long, deviceWorker: ActorRef): Unit = map.get(id) match {

    case Some(deviceWorker) => {
      val e = new Exception(s"Remote address id $id is already known (served by worker actor $deviceWorker), could not add it again.")
      log.error(e, e.getMessage)
    }

    case None => {
      map.put(id, deviceWorker)
      log.debug(s"Remote address id $id was added (worker actor $deviceWorker), currently there are ${map.size} opened sockets (ids: ${map.keySet.mkString(",")}).")

      amaConfig.broadcaster ! new InactivityMonitor.StartInactivityMonitor(id, warningTimeAfterMs, inactivityTimeAfterMs)
    }
  }

  protected def deviceStopped(deviceStoppedMessage: DeviceSpec.DeviceStopped): Unit = deviceStopped(deviceStoppedMessage.request1.message.connectionInfo.remote.id)

  protected def deviceStopped(id: Long): Unit = map.remove(id).map { deviceWorker =>
    log.debug(s"Remote address id $id was removed (worker actor $deviceWorker), currently there are ${map.size} opened sockets (ids: ${map.keySet.mkString(",")}).")
    amaConfig.broadcaster ! new InactivityMonitor.StopInactivityMonitor(id)
  }
}
