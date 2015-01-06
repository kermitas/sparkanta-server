/*
package as.sparkanta.actor.inactivity

import as.ama.addon.lifecycle.ShutdownSystem

import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{ ActorRef, ActorLogging, Actor, Cancellable }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.collection.mutable.Map
import akka.util.{ IncomingMessage, IncomingReplyableMessage, InternalMessage, OutgoingReplyOn1Message }

object InactivityMonitor {

  class StartInactivityMonitor(val id: Long, val warningTimeAfterMs: Long, val inactivityTimeAfterMs: Long) extends IncomingReplyableMessage
  abstract class StartInactivityMonitorResult(val optionalException: Option[Exception], startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends OutgoingReplyOn1Message(startInactivityMonitor, startInactivityMonitorSender)
  class StartInactivityMonitorSuccessResult(startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends StartInactivityMonitorResult(None, startInactivityMonitor, startInactivityMonitorSender)
  class StartInactivityMonitorErrorResult(val exception: Exception, startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends StartInactivityMonitorResult(Some(exception), startInactivityMonitor, startInactivityMonitorSender)

  class Active(val id: Long) extends IncomingMessage
  class InactivityWarning(val inactivityTimeInMs: Long, startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends OutgoingReplyOn1Message(startInactivityMonitor, startInactivityMonitorSender)
  class InactivityDetected(val inactivityTimeInMs: Long, startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends OutgoingReplyOn1Message(startInactivityMonitor, startInactivityMonitorSender)

  class StopInactivityMonitor(val id: Long) extends IncomingMessage
  // TODO should send back also result of stopping?

  class WarningTimeout(val record: Record) extends InternalMessage
  class InactivityTimeout(val record: Record) extends InternalMessage

  class Record(var warningTimer: Option[Cancellable], var inactivityTimer: Option[Cancellable], val startInactivityMonitor: StartInactivityMonitor, val startInactivityMonitorSender: ActorRef, var lastActiveTime: Long)
}

class InactivityMonitor(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import InactivityMonitor._

  protected val map = Map[Long, Record]()

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new InactivityMonitorClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: StartInactivityMonitor => startInactivityMonitor(a, sender)
    case a: Active                 => active(a.id)
    case a: StopInactivityMonitor  => stopInactivityMonitor(a.id)
    case a: WarningTimeout         => warningTimeout(a.record)
    case a: InactivityTimeout      => inactivityTimeout(a.record)
    case message                   => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startInactivityMonitor(startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) = try {
    if (map.contains(startInactivityMonitor.id)) {
      throw new Exception(s"Id ${startInactivityMonitor.id} is already registered, could not register new.")
    } else {
      val record = new Record(None, None, startInactivityMonitor, startInactivityMonitorSender, System.currentTimeMillis)
      putToMap(startInactivityMonitor.id, record)
      setTimers(record)
      val startInactivityMonitorSuccessResult = new StartInactivityMonitorSuccessResult(startInactivityMonitor, startInactivityMonitorSender)
      startInactivityMonitorSuccessResult.reply(self)
    }
  } catch {
    case e: Exception => {
      val startInactivityMonitorErrorResult = new StartInactivityMonitorErrorResult(e, startInactivityMonitor, startInactivityMonitorSender)
      startInactivityMonitorErrorResult.reply(self)
    }
  }

  protected def stopInactivityMonitor(id: Long): Unit = map.get(id).map { record =>
    cancelTimers(record)
    removeFromMap(id)
  }

  protected def active(id: Long): Unit = map.get(id).map { record =>
    record.lastActiveTime = System.currentTimeMillis
    resetTimers(record)
  }

  protected def cancelTimers(record: Record): Unit = {
    record.warningTimer.map(_.cancel)
    record.inactivityTimer.map(_.cancel)
  }

  protected def setTimers(record: Record): Unit = {
    import context.dispatcher
    record.warningTimer = Some(context.system.scheduler.scheduleOnce(record.startInactivityMonitor.warningTimeAfterMs millis, self, new WarningTimeout(record)))
    record.inactivityTimer = Some(context.system.scheduler.scheduleOnce(record.startInactivityMonitor.inactivityTimeAfterMs millis, self, new InactivityTimeout(record)))
  }

  protected def resetTimers(record: Record): Unit = {
    cancelTimers(record)
    setTimers(record)
  }

  protected def warningTimeout(record: Record): Unit = {
    record.warningTimer = None

    val inactivityWarning = new InactivityWarning(System.currentTimeMillis - record.lastActiveTime, record.startInactivityMonitor, record.startInactivityMonitorSender)
    inactivityWarning.reply(self)
  }

  protected def inactivityTimeout(record: Record): Unit = {

    val inactivityDetected = new InactivityDetected(System.currentTimeMillis - record.lastActiveTime, record.startInactivityMonitor, record.startInactivityMonitorSender)
    inactivityDetected.reply(self)

    stopInactivityMonitor(record.startInactivityMonitor.id)
  }

  protected def putToMap(id: Long, record: Record): Unit = {
    map.put(id, record)
    log.debug(s"Id $id was added, currently there are ${map.size} ids in map (ids: ${map.keySet.mkString(",")}).")
  }

  protected def removeFromMap(id: Long): Unit = map.remove(id).map { _ =>
    log.debug(s"Id $id was removed, currently there are ${map.size} ids in map (ids: ${map.keySet.mkString(",")}).")
  }
}
*/ 