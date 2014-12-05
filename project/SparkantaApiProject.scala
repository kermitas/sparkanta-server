import sbt._
import Keys._

object SparkantaApiProject {

  lazy final val projectName = SparkantaProject.projectName + "-api"

  def apply(version: String, scalaUtils: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(scalaUtils),
      dependencies = Seq(scalaUtils),
      delegates    = Seq(scalaUtils),

      settings     = CommonSettings(projectName, version)
    )
}
