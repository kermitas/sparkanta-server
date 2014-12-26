import sbt._
import Keys._

object TypesafeConfigSettings {
  def apply() = Seq(
    libraryDependencies += "com.typesafe" % "config" % "1.2.1"
  )
}
