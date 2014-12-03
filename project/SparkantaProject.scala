import sbt._
import Keys._

object SparkantaProject {

  lazy final val projectName = "sparkanta"

  def apply(version: String, sparkantaServer: Project) =
    Project(
      id           = projectName,
      base         = file("."),

      aggregate    = Seq(sparkantaServer),
      dependencies = Seq(sparkantaServer),
      delegates    = Seq(sparkantaServer),

      settings     = CommonSettings(projectName, version)
    )
}
