import sbt._
import Keys._

object AmaSettings {
  def apply() = Seq(
    libraryDependencies += "as.ama" %% "ama" % "0.4.8"
  )
}
