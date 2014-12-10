import sbt._
import Keys._

object SpraySettings {

  lazy final val sprayVersion = "1.3.2"

  def client = sprayCommons ++ Seq(

    libraryDependencies ++= Seq(
      "io.spray" %% "spray-client" % sprayVersion
    )
  )

  protected def sprayCommons = Seq(
    resolvers += "spray repo" at "http://repo.spray.io",

    libraryDependencies ++= Seq(
      "io.spray" %% "spray-util" % sprayVersion,
      "io.spray" %% "spray-can" % sprayVersion,
      "io.spray" %% "spray-http" % sprayVersion,
      "io.spray" %% "spray-httpx" % sprayVersion
    )
  )
}
