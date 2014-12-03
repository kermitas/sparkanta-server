import sbt._
import Keys._

object SparkantaApiProject {

  lazy final val projectName = SparkantaProject.projectName + "-api"

  def apply(version: String) =
    Project(
      id       = projectName,
      base     = file(projectName),

      settings = CommonSettings(projectName, version)
    )
}
