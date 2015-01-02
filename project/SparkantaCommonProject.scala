import sbt._
import Keys._

object SparkantaCommonProject {

  lazy final val projectName = SparkantaProject.projectName + "-common"

  def apply(version: String, akkaUtils: Project, scalaUtils: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(akkaUtils, scalaUtils),
      dependencies = Seq(akkaUtils, scalaUtils),
      delegates    = Seq(akkaUtils, scalaUtils),

      settings     = CommonSettings(projectName, version)
    )
}
