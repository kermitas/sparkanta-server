import sbt._
import Keys._

object AmaSettings {

  lazy final val amaVersion = "0.5.0"

  def main() = Seq(
    libraryDependencies += "as.ama" %% "ama" % amaVersion
  )

  def akka() = Seq(
    libraryDependencies += "as.ama" %% "ama-akka" % amaVersion
  )
}
