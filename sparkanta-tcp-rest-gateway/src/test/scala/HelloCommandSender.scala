import org.scalatest.{ FeatureSpec, Matchers }

import java.net.Socket

import scala.io.StdIn

class HelloCommandSender extends FeatureSpec with Matchers {

  scenario("sending hello command") {

    val helloAsByteArray = Array[Byte](0.toByte, 8.toByte, 1.toByte, 1.toByte, 1.toByte, 4.toByte, 'A'.toByte, 'B'.toByte, 'C'.toByte, 'D'.toByte)

    val socket = new Socket("localhost", 8080)

    socket.getOutputStream.write(helloAsByteArray)
    socket.getOutputStream.flush

    StdIn.readLine()
  }

}