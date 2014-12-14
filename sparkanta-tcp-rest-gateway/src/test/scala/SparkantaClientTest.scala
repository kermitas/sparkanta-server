import org.scalatest.{ FeatureSpec, Matchers }

import java.net.Socket
import scala.io.StdIn
import as.sparkanta.device.message.{ DeviceHello, Ping }
import as.sparkanta.device.message.length.Message65536LengthHeaderCreator
import as.sparkanta.device.message.serialize.Serializers

class SparkantaClientTest extends FeatureSpec with Matchers {

  scenario("sparkanta client") {

    val identificationStringAsByteArray = "SPARKANTA".getBytes
    val softwareVersionAsByteArray = Array[Byte](1.toByte)

    val serializers = new Serializers
    val messageLengthHeaderCreator = new Message65536LengthHeaderCreator

    val deviceHelloMessageAsByteArray = {
      val messageAsByteArray = {
        val message = new DeviceHello("Alice has a cat")
        serializers.serialize(message)
      }

      val messageLengthHeader = messageLengthHeaderCreator.prepareMessageLengthHeader(messageAsByteArray.length)

      Seq(messageLengthHeader, messageAsByteArray)
    }

    val pingMessageAsByteArray = {
      val messageAsByteArray = {
        val message = new Ping
        serializers.serialize(message)
      }

      val messageLengthHeader = messageLengthHeaderCreator.prepareMessageLengthHeader(messageAsByteArray.length)

      Seq(messageLengthHeader, messageAsByteArray)
    }

    val os = {
      val socket = new Socket("localhost", 8080)
      socket.getOutputStream
    }

    os.write(identificationStringAsByteArray)
    os.write(softwareVersionAsByteArray)

    deviceHelloMessageAsByteArray.foreach(os.write)
    pingMessageAsByteArray.foreach(os.write)

    StdIn.readLine()
  }

}