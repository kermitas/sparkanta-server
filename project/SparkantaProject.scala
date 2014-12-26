import sbt._
import Keys._

object SparkantaProject {

  lazy final val projectName = "sparkanta"

  def apply(version: String, gateway: Project, server: Project) =
    Project(
      id           = projectName,
      base         = file("."),

      aggregate    = Seq(gateway, server),
      dependencies = Seq(gateway, server),
      delegates    = Seq(gateway, server),

      settings     = CommonSettings(projectName, version)
    )
}
