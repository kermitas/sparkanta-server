import sbt._
import Keys._

object SparkantaProject {

  lazy final val projectName = "sparkanta"

  def apply(version: String, sparkantaTcpRestGateway: Project) =
    Project(
      id           = projectName,
      base         = file("."),

      aggregate    = Seq(sparkantaTcpRestGateway),
      dependencies = Seq(sparkantaTcpRestGateway),
      delegates    = Seq(sparkantaTcpRestGateway),

      settings     = CommonSettings(projectName, version)
    )
}
