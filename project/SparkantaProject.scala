import sbt._
import Keys._

object SparkantaProject {

  lazy final val projectName = "sparkanta"

  def apply(version: String, sparkantaTcpRestGateway: Project, sparkantaRestServer: Project) =
    Project(
      id           = projectName,
      base         = file("."),

      aggregate    = Seq(sparkantaTcpRestGateway, sparkantaRestServer),
      dependencies = Seq(sparkantaTcpRestGateway, sparkantaRestServer),
      delegates    = Seq(sparkantaTcpRestGateway, sparkantaRestServer),

      settings     = CommonSettings(projectName, version)
    )
}
