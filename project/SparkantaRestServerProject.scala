import sbt._
import Keys._

object SparkantaRestServerProject {

  lazy final val projectName = SparkantaProject.projectName + "-rest-server"

  def apply(version: String, sparkantaApi: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(sparkantaApi),
      dependencies = Seq(sparkantaApi),
      delegates    = Seq(sparkantaApi),

      settings     = CommonSettings(projectName, version)
    )
}
