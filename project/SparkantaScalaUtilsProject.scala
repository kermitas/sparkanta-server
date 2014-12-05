import sbt._
import Keys._

object SparkantaScalaUtilsProject {

  lazy final val projectName = SparkantaProject.projectName + "-scala-utils"

  def apply(version: String) =
    Project(
      id       = projectName,
      base     = file(projectName),

      settings = CommonSettings(projectName, version)
    )
}
