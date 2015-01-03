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
  class Active(val id: Long) extends IncomingMessage
  class StopInactivityMonitor(val id: Long) extends IncomingMessage
  class InactivityWarning(val inactivityTimeInMs: Long, startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends OutgoingReplyOn1Message(startInactivityMonitor, startInactivityMonitorSender)
  class InactivityDetected(val inactivityTimeInMs: Long, startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends OutgoingReplyOn1Message(startInactivityMonitor, startInactivityMonitorSender)

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

  protected def startInactivityMonitor(startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) {

    stopInactivityMonitor(startInactivityMonitor.id)

    val recordCreator = () => new Record(None, None, startInactivityMonitor, startInactivityMonitorSender, System.currentTimeMillis)
    val record = getOrCreateRecord(startInactivityMonitor.id, recordCreator)

    setTimers(record)
  }

  protected def getOrCreateRecord(id: Long, recordCreator: () => Record): Record = map.get(id) match {
    case Some(record) => record

    case None => {
      val record = recordCreator()
      map.put(id, record)
      record
    }
  }

  protected def stopInactivityMonitor(id: Long): Unit = map.get(id).map { record =>
    cancelTimers(record)
    map.remove(id)
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
}
