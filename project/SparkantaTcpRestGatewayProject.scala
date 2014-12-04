import sbt._
import Keys._

object SparkantaTcpRestGatewayProject {

  lazy final val projectName                 = SparkantaProject.projectName + "-tcp-rest-gateway"
  lazy final val mainClassFullyQualifiedName = "as.ama.Main"

  def apply(version: String, sparkantaApi: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(sparkantaApi),
      dependencies = Seq(sparkantaApi),
      delegates    = Seq(sparkantaApi),

      settings     = CommonSettings(projectName, version) ++
                     ScalaTestSettings() ++
                     AmaSettings() ++
                     AkkaSlf4JSettings() ++
                     LogbackClassicSettings() ++
                     mainClassSettings(mainClassFullyQualifiedName) ++
                     PackSettings(mainClassFullyQualifiedName) ++
                     AssemblySettings(mainClassFullyQualifiedName)
    )

  protected def mainClassSettings(mainClassFullyQualifiedName: String) = Seq (
    mainClass in (Compile,run) := Some(mainClassFullyQualifiedName)
  )
}
