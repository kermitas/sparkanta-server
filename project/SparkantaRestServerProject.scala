import sbt._
import Keys._

object SparkantaRestServerProject {

  lazy final val projectName = SparkantaProject.projectName + "-rest-server"

  def apply(version: String, api: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(api),
      dependencies = Seq(api),
      delegates    = Seq(api),

      settings     = CommonSettings(projectName, version)
    )
}
