import sbt._
import Keys._

object SparkantaProject {

  lazy final val projectName = "sparkanta"

  def apply(version: String, tcpRestGateway: Project, restServer: Project) =
    Project(
      id           = projectName,
      base         = file("."),

      aggregate    = Seq(tcpRestGateway, restServer),
      dependencies = Seq(tcpRestGateway, restServer),
      delegates    = Seq(tcpRestGateway, restServer),

      settings     = CommonSettings(projectName, version)
    )
}
