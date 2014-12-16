import org.scalatest.{ FeatureSpec, Matchers }

import java.net.Socket
import scala.io.StdIn
import as.sparkanta.device.message.{ DeviceHello, Ping }
import as.sparkanta.device.message.length.Message255LengthHeaderCreator
import as.sparkanta.device.message.serialize.Serializers

class SparkantaClientTest extends FeatureSpec with Matchers {

  scenario("sparkanta client") {

    val identificationStringAsByteArray = "SPARKANTA".getBytes
    val softwareVersionAsByteArray = Array[Byte](1.toByte)
    val hardwareVersionAsByteArray = Array[Byte](0.toByte)

    val serializers = new Serializers
    val messageLengthHeaderCreator = new Message255LengthHeaderCreator

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

    println("Connecting...")

    val os = {
      val socket = new Socket("192.168.2.25", 8080)
      socket.getOutputStream
    }

    println("Sending data...")

    os.write(identificationStringAsByteArray)
    os.write(softwareVersionAsByteArray)
    os.write(hardwareVersionAsByteArray)

    deviceHelloMessageAsByteArray.foreach(os.write)
    os.flush

    Thread.sleep(500)

    pingMessageAsByteArray.foreach(os.write)
    os.flush

    StdIn.readLine()
  }

}