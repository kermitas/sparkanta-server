package as.sparkanta.actor.message

import as.ama.addon.lifecycle.ShutdownSystem

import scala.util.{ Try, Success, Failure }
import akka.util.ByteString
import akka.actor.{ ActorRef, ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.collection.mutable.{ Map, ListBuffer }
import akka.util.{ IncomingMessage, IncomingReplyableMessage, OutgoingReplyOn1Message }

object MessageDataAccumulator {

  class StartDataAccumulation(val id: Long) extends IncomingReplyableMessage
  abstract class StartDataAccumulationResult(val exception: Option[Exception], startDataAccumulation: StartDataAccumulation, startDataAccumulationSender: ActorRef) extends OutgoingReplyOn1Message(startDataAccumulation, startDataAccumulationSender)
  class StartDataAccumulationSuccessResult(startDataAccumulation: StartDataAccumulation, startDataAccumulationSender: ActorRef) extends StartDataAccumulationResult(None, startDataAccumulation, startDataAccumulationSender)
  class StartDataAccumulationErrorResult(exception: Exception, startDataAccumulation: StartDataAccumulation, startDataAccumulationSender: ActorRef) extends StartDataAccumulationResult(Some(exception), startDataAccumulation, startDataAccumulationSender)

  class AccumulateMessageData(val id: Long, val messageData: ByteString) extends IncomingReplyableMessage
  abstract class MessageDataAccumulationResult(val messageData: Try[Seq[Array[Byte]]], accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef) extends OutgoingReplyOn1Message(accumulateMessageData, accumulateMessageDataSender)
  class MessageDataAccumulationSuccessResult(messageData: Seq[Array[Byte]], accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef) extends MessageDataAccumulationResult(Success(messageData), accumulateMessageData, accumulateMessageDataSender)
  class MessageDataAccumulationErrorResult(val exception: Exception, accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef) extends MessageDataAccumulationResult(Failure(exception), accumulateMessageData, accumulateMessageDataSender)

  class StopDataAccumulation(val id: Long) extends IncomingReplyableMessage
  abstract class StopDataAccumulationResult(val exception: Option[Exception], stopDataAccumulation: StopDataAccumulation, stopDataAccumulationSender: ActorRef) extends OutgoingReplyOn1Message(stopDataAccumulation, stopDataAccumulationSender)
  class StopDataAccumulationSuccessResult(stopDataAccumulation: StopDataAccumulation, stopDataAccumulationSender: ActorRef) extends StopDataAccumulationResult(None, stopDataAccumulation, stopDataAccumulationSender)
  class StopDataAccumulationErrorResult(exception: Exception, stopDataAccumulation: StopDataAccumulation, stopDataAccumulationSender: ActorRef) extends StopDataAccumulationResult(Some(exception), stopDataAccumulation, stopDataAccumulationSender)

  class Record(var buffer: ByteString)
}

class MessageDataAccumulator(amaConfig: AmaConfig) extends Actor with ActorLogging {

  import MessageDataAccumulator._

  protected val map = Map[Long, Record]()

  override def preStart(): Unit = try {
    amaConfig.broadcaster ! new Broadcaster.Register(self, new MessageDataAccumulatorClassifier(amaConfig.broadcaster))
    amaConfig.sendInitializationResult()
  } catch {
    case e: Exception => amaConfig.sendInitializationResult(new Exception(s"Problem while installing ${getClass.getSimpleName} actor.", e))
  }

  override def postStop(): Unit = {
    amaConfig.broadcaster ! new ShutdownSystem(Left(new Exception(s"Shutting down JVM because actor ${getClass.getSimpleName} was stopped.")))
  }

  override def receive = {
    case a: AccumulateMessageData => accumulateMessageDataAndSendResponse(a, sender)
    case a: StartDataAccumulation => startDataAccumulation(a, sender)
    case a: StopDataAccumulation  => stopDataAccumulation(a, sender)
    case message                  => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def startDataAccumulation(startDataAccumulation: StartDataAccumulation, startDataAccumulationSender: ActorRef): Unit = try {
    if (map.contains(startDataAccumulation.id)) {
      throw new Exception(s"Id ${startDataAccumulation.id} is already registered.")
    } else {
      val record = new Record(ByteString.empty)
      map.put(startDataAccumulation.id, record)
      val startDataAccumulationSuccessResult = new StartDataAccumulationSuccessResult(startDataAccumulation, startDataAccumulationSender)
      startDataAccumulationSuccessResult.reply(self)
    }
  } catch {
    case e: Exception => {
      val messageDataAccumulationErrorResult = new StartDataAccumulationErrorResult(e, startDataAccumulation, startDataAccumulationSender)
      messageDataAccumulationErrorResult.reply(self)
    }
  }

  protected def stopDataAccumulation(stopDataAccumulation: StopDataAccumulation, stopDataAccumulationSender: ActorRef): Unit = try {
    if (map.contains(stopDataAccumulation.id)) {
      map.remove(stopDataAccumulation.id)
      val stopDataAccumulationSuccessResult = new StopDataAccumulationSuccessResult(stopDataAccumulation, stopDataAccumulationSender)
      stopDataAccumulationSuccessResult.reply(self)
    } else {
      throw new Exception(s"Id ${stopDataAccumulation.id} is not registered.")
    }
  } catch {
    case e: Exception => {
      val stopDataAccumulationErrorResult = new StopDataAccumulationErrorResult(e, stopDataAccumulation, stopDataAccumulationSender)
      stopDataAccumulationErrorResult.reply(self)
    }
  }

  protected def accumulateMessageDataAndSendResponse(accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef): Unit = try {
    val messageDataAccumulationResult = performMessageDataAccumulation(accumulateMessageData, accumulateMessageDataSender)
    messageDataAccumulationResult.reply(self)
  } catch {
    case e: Exception => log.error(e, "Problem during accumulation of message data.")
  }

  protected def performMessageDataAccumulation(accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef): MessageDataAccumulationResult = try {
    val record = map.get(accumulateMessageData.id) match {
      case Some(record) => record
      case None         => throw new Exception(s"Id ${accumulateMessageData.id} is not registered.")
    }

    record.buffer = record.buffer ++ accumulateMessageData.messageData

    log.debug(s"There are ${record.buffer.size} accumulated bytes after accumulating new ${accumulateMessageData.messageData.size} bytes for id ${accumulateMessageData.id}.")

    val accumulatedMessageData = analyzeRecord(record, accumulateMessageData.id)

    new MessageDataAccumulationSuccessResult(accumulatedMessageData, accumulateMessageData, accumulateMessageDataSender)
  } catch {
    case e: Exception => new MessageDataAccumulationErrorResult(e, accumulateMessageData, accumulateMessageDataSender)
  }

  protected def analyzeRecord(record: Record, id: Long): Seq[Array[Byte]] = {

    var bufferSize = record.buffer.size

    if (bufferSize > 0) {
      val result = new ListBuffer[Array[Byte]]

      log.debug(s"Message for id $id will have ${record.buffer(0)} bytes (${record.buffer(0) + 1 - bufferSize} bytes needs to be collected to have full message).")

      while (bufferSize > 0 && bufferSize >= record.buffer(0) + 1) {
        val messageLength = record.buffer(0)

        log.debug(s"Got all $messageLength bytes for incoming message for id $id.")

        val messageDataAndRest = record.buffer.drop(1).splitAt(messageLength)

        result += messageDataAndRest._1.toArray
        record.buffer = messageDataAndRest._2

        bufferSize = record.buffer.size
      }

      result
    } else {
      Seq.empty

    }
  }
}
