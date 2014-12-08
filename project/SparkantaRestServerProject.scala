import sbt._
import Keys._

object SparkantaRestServerProject {

  lazy final val projectName = SparkantaProject.projectName + "-rest-server"

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
