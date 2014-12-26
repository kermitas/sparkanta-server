import sbt._
import Keys._

object SparkantaServerProject {

  lazy final val projectName = SparkantaProject.projectName + "-server"

  def apply(version: String, common: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(common),
      dependencies = Seq(common),
      delegates    = Seq(common),

      settings     = CommonSettings(projectName, version)
    )
}
