import sbt._
import Keys._

object AmaSettings {

  lazy final val amaVersion = "0.6.0"

  def apply() = Seq(
    libraryDependencies += "as.ama" %% "ama" % amaVersion
  )
}
