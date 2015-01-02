package as.sparkanta.actor2.message

import scala.util.{ Try, Success, Failure }
import akka.util.ByteString
import akka.actor.{ ActorRef, ActorLogging, Actor }
import as.akka.broadcaster.Broadcaster
import as.sparkanta.ama.config.AmaConfig
import scala.collection.mutable.{ Map, ListBuffer }
import akka.util.{ IncomingMessage, IncomingReplyableMessage, OutgoingReplyOn1Message }

object MessageDataAccumulator {
  class AccumulateMessageData(val messageData: ByteString, val id: Long) extends IncomingReplyableMessage
  abstract class MessageDataAccumulationResult(val messageData: Try[Seq[Array[Byte]]], accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef) extends OutgoingReplyOn1Message(accumulateMessageData, accumulateMessageDataSender)
  class MessageDataAccumulationSuccessResult(messageData: Seq[Array[Byte]], accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef) extends MessageDataAccumulationResult(Success(messageData), accumulateMessageData, accumulateMessageDataSender)
  class MessageDataAccumulationErrorResult(exception: Exception, accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef) extends MessageDataAccumulationResult(Failure(exception), accumulateMessageData, accumulateMessageDataSender)
  class ClearData(val id: Long) extends IncomingMessage

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

  override def receive = {
    case a: AccumulateMessageData => accumulateMessageDataAndSendResponse(a, sender)
    case a: ClearData             => map.remove(a.id)
    case message                  => log.warning(s"Unhandled $message send by ${sender()}")
  }

  protected def accumulateMessageDataAndSendResponse(accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef): Unit = try {
    val messageDataAccumulationResult = performMessageDataAccumulation(accumulateMessageData, accumulateMessageDataSender)
    messageDataAccumulationResult.reply(self)
  } catch {
    case e: Exception => log.error("Problem during accumulation of message data.", e)
  }

  protected def performMessageDataAccumulation(accumulateMessageData: AccumulateMessageData, accumulateMessageDataSender: ActorRef): MessageDataAccumulationResult = try {
    val record = getOrCreateRecord(accumulateMessageData.id)
    record.buffer = record.buffer ++ accumulateMessageData.messageData
    val accumulatedMessageData = analyzeRecord(record)

    new MessageDataAccumulationSuccessResult(accumulatedMessageData, accumulateMessageData, accumulateMessageDataSender)
  } catch {
    case e: Exception => new MessageDataAccumulationErrorResult(e, accumulateMessageData, accumulateMessageDataSender)
  }

  protected def getOrCreateRecord(id: Long): Record = map.get(id) match {
    case Some(record) => record

    case None => {
      val record = new Record(ByteString.empty)
      map.put(id, record)
      record
    }
  }

  protected def analyzeRecord(record: Record): Seq[Array[Byte]] = {

    val result = ListBuffer[Array[Byte]]()

    var bufferSize = record.buffer.size

    while (bufferSize > 0 && bufferSize >= record.buffer(0) + 1) {
      val messageLength = record.buffer(0)
      val messageDataAndRest = record.buffer.drop(1).splitAt(messageLength)

      result += messageDataAndRest._1.toArray
      record.buffer = messageDataAndRest._2

      bufferSize = record.buffer.size
    }

    result
  }
}
