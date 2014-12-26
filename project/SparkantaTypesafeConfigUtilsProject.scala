import sbt._
import Keys._

object SparkantaTypesafeConfigUtilsProject {

  lazy final val projectName = SparkantaProject.projectName + "-typesafe-config-utils"

  def apply(version: String) =
    Project(
      id       = projectName,
      base     = file(projectName),

      settings = CommonSettings(projectName, version) ++
                 TypesafeConfigSettings()
    )
}
