import sbt._
import Keys._

object SparkantaCommonProject {

  lazy final val projectName = SparkantaProject.projectName + "-common"

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
