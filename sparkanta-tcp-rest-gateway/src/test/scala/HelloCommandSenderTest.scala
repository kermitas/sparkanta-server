import org.scalatest.{ FeatureSpec, Matchers }

import java.net.Socket
import scala.io.StdIn
import as.sparkanta.device.message.{ Hello, MessageHeader65536 }
import as.sparkanta.device.message.serialize.HelloSerializerVersion1

class HelloCommandSenderTest extends FeatureSpec with Matchers {

  scenario("sending hello command") {

    val identificationStringWithSoftwareVersion = {
      val identificationString = "SPARKANTA"
      val softwareVersion: Byte = 12

      identificationString.getBytes ++ Array[Byte](softwareVersion.toByte)
    }

    val helloMessageAsByteArray = {
      val hello = new Hello("Alice has a cat")
      val helloAsByteArray = new HelloSerializerVersion1().serialize(hello)
      new MessageHeader65536().prepareMessageToGo(helloAsByteArray)
    }

    val socket = new Socket("localhost", 8080)

    socket.getOutputStream.write(identificationStringWithSoftwareVersion)
    socket.getOutputStream.write(helloMessageAsByteArray)
    socket.getOutputStream.flush

    StdIn.readLine()
  }

}