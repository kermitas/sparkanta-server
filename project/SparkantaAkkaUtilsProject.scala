import sbt._
import Keys._

object SparkantaAkkaUtilsProject {

  lazy final val projectName = SparkantaProject.projectName + "-akka-utils"

  def apply(version: String) =
    Project(
      id       = projectName,
      base     = file(projectName),

      settings = CommonSettings(projectName, version) ++
                 AkkaSettings() ++
                 AmaSettings.akka()
    )
}
