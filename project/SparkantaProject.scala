import sbt._
import Keys._

object SparkantaProject {

  lazy final val projectName = "sparkanta"

  def apply(version: String, gateway: Project, restServer: Project) =
    Project(
      id           = projectName,
      base         = file("."),

      aggregate    = Seq(gateway, restServer),
      dependencies = Seq(gateway, restServer),
      delegates    = Seq(gateway, restServer),

      settings     = CommonSettings(projectName, version)
    )
}
