import org.scalatest.{ FeatureSpec, Matchers }

import java.net.Socket
import scala.io.StdIn
import as.sparkanta.device.message.{ Hello, MessageHeader65536 }
import as.sparkanta.device.message.serialize.HelloSerializerVersion1

class HelloCommandSenderTest extends FeatureSpec with Matchers {

  scenario("sending hello command") {

    val hello = new Hello("Alice has a cat")
    val helloAsByteArray = new HelloSerializerVersion1().serialize(hello)
    val datagram = new MessageHeader65536().prepareMessageToGo(helloAsByteArray)

    //val helloAsByteArray = Array[Byte](0.toByte, 8.toByte, 1.toByte, 1.toByte, 1.toByte, 4.toByte, 'A'.toByte, 'B'.toByte, 'C'.toByte, 'D'.toByte)

    val socket = new Socket("localhost", 8080)

    socket.getOutputStream.write(datagram)
    socket.getOutputStream.flush

    StdIn.readLine()
  }

}