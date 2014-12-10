import sbt._
import Keys._

object SparkantaTcpRestGatewayProject {

  lazy final val projectName                 = SparkantaProject.projectName + "-tcp-rest-gateway"
  lazy final val mainClassFullyQualifiedName = "as.ama.Main"

  def apply(version: String, common: Project, akkaUtils: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(common, akkaUtils),
      dependencies = Seq(common, akkaUtils),
      delegates    = Seq(common, akkaUtils),

      settings     = CommonSettings(projectName, version) ++
                     ScalaTestSettings() ++
                     AmaSettings() ++
                     AkkaSlf4JSettings() ++
                     LogbackClassicSettings() ++
                     SpraySettings.client ++
                     mainClassSettings(mainClassFullyQualifiedName) ++
                     PackSettings(mainClassFullyQualifiedName) ++
                     AssemblySettings(mainClassFullyQualifiedName)
    )

  protected def mainClassSettings(mainClassFullyQualifiedName: String) = Seq (
    mainClass in (Compile,run) := Some(mainClassFullyQualifiedName)
  )
}
