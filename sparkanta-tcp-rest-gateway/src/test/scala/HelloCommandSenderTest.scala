import org.scalatest.{ FeatureSpec, Matchers }

import java.net.Socket
import scala.io.StdIn
import as.sparkanta.device.message.{ Hello, Ping, Message65536LengthHeader }
import as.sparkanta.device.message.serialize.Serializers

class HelloCommandSenderTest extends FeatureSpec with Matchers {

  scenario("sending hello command") {

    val identificationStringWithSoftwareVersion = {
      val identificationString = "SPARKANTA"
      val softwareVersion: Byte = 1

      identificationString.getBytes ++ Array[Byte](softwareVersion.toByte)
    }

    val serializers = new Serializers
    val messageLengthHeader = new Message65536LengthHeader

    val helloMessageAsByteArray = {
      val message = new Hello("Alice has a cat")
      val messageAsByteArray = serializers.serialize(message)
      messageLengthHeader.prepareMessageToGo(messageAsByteArray)
    }

    val pingMessageAsBytes = {
      val message = new Ping
      val messageAsByteArray = serializers.serialize(message)
      messageLengthHeader.prepareMessageToGo(messageAsByteArray)
    }

    val socket = new Socket("localhost", 8080)

    socket.getOutputStream.write(identificationStringWithSoftwareVersion)
    socket.getOutputStream.write(helloMessageAsByteArray)
    socket.getOutputStream.write(pingMessageAsBytes)
    socket.getOutputStream.flush

    StdIn.readLine()
  }

}