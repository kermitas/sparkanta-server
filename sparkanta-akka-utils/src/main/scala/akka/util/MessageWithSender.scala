package akka.util

import akka.actor.ActorRef

class MessageWithSender[T](message: T, messageSender: ActorRef) extends Serializable
