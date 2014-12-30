package as.sparkanta.actor2.inactivity

import scala.language.postfixOps
import scala.concurrent.duration._
import as.ama.util.FromBroadcaster
import akka.actor.{ ActorRef, ActorLogging, Actor, Cancellable }
import akka.util.ReplyOn1Impl
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.collection.mutable.Map

object InactivityMonitor {
  trait Message extends Serializable
  trait IncomingMessage extends Message
  trait InternalMessage extends IncomingMessage
  trait OutgoingMessage extends Message

  class StartInactivityMonitor(val id: Long, val warningTimeAfterMs: Long, val inactivityTimeAfterMs: Long) extends IncomingMessage
  class StartInactivityMonitorFromBroadcaster(startInactivityMonitor: StartInactivityMonitor) extends FromBroadcaster[StartInactivityMonitor](startInactivityMonitor) with IncomingMessage
  class Active(val id: Long) extends IncomingMessage
  class StopInactivityMonitor(val id: Long) extends IncomingMessage
  class InactivityWarning(val inactivityTimeInMs: Long, startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends ReplyOn1Impl[StartInactivityMonitor](startInactivityMonitor, startInactivityMonitorSender) with OutgoingMessage
  class InactivityDetected(val inactivityTimeInMs: Long, startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef) extends ReplyOn1Impl[StartInactivityMonitor](startInactivityMonitor, startInactivityMonitorSender) with OutgoingMessage

  class Record(var warningTimer: Option[Cancellable], var inactivityTimer: Option[Cancellable], val startInactivityMonitor: StartInactivityMonitor, val startInactivityMonitorSender: ActorRef, val publishReplyOnBroadcaster: Boolean, var lastActiveTime: Long)
  class WarningTimeout(val record: Record) extends InternalMessage
  class InactivityTimeout(val record: Record) extends InternalMessage
}

class InactivityMonitor(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import InactivityMonitor._

  protected val map = Map[Long, Record]()

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new InactivityMonitorClassifier)
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def receive = {
    case a: StartInactivityMonitorFromBroadcaster => startInactivityMonitor(a.message, sender, true)
    case a: StartInactivityMonitor                => startInactivityMonitor(a, sender, false)
    case a: Active                                => active(a.id)
    case a: StopInactivityMonitor                 => stopInactivityMonitor(a.id)
    case a: WarningTimeout                        => warningTimeout(a.record)
    case a: InactivityTimeout                     => inactivityTimeout(a.record)
    case message                                  => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startInactivityMonitor(startInactivityMonitor: StartInactivityMonitor, startInactivityMonitorSender: ActorRef, publishReplyOnBroadcaster: Boolean) {

    stopInactivityMonitor(startInactivityMonitor.id)

    val recordCreator = () => new Record(None, None, startInactivityMonitor, startInactivityMonitorSender, publishReplyOnBroadcaster, System.currentTimeMillis)
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

  protected def stopInactivityMonitor(id: Long): Unit = map.get(id) match {
    case Some(record) => {
      cancelTimers(record)
      map.remove(id)
    }

    case None =>
  }

  protected def active(id: Long): Unit = map.get(id) match {
    case Some(record) => {
      record.lastActiveTime = System.currentTimeMillis
      resetTimers(record)
    }

    case None =>
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

    record.startInactivityMonitorSender ! inactivityWarning
    if (record.publishReplyOnBroadcaster) amaConfig.broadcaster ! inactivityWarning
  }

  protected def inactivityTimeout(record: Record): Unit = {

    val inactivityDetected = new InactivityDetected(System.currentTimeMillis - record.lastActiveTime, record.startInactivityMonitor, record.startInactivityMonitorSender)

    record.startInactivityMonitorSender ! inactivityDetected
    if (record.publishReplyOnBroadcaster) amaConfig.broadcaster ! inactivityDetected

    stopInactivityMonitor(record.startInactivityMonitor.id)
  }
}
