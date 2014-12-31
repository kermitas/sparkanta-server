import sbt._
import Keys._

object SparkantaGatewayProject {

  lazy final val projectName                 = SparkantaProject.projectName + "-gateway"
  lazy final val mainClassFullyQualifiedName = "as.ama.Main"

  def apply(version: String, common: Project, akkaUtils: Project, typesafeUtils: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(common, akkaUtils, typesafeUtils),
      dependencies = Seq(common, akkaUtils, typesafeUtils),
      delegates    = Seq(common, akkaUtils, typesafeUtils),

      settings     = CommonSettings(projectName, version) ++
                     ScalaTestSettings() ++
                     AmaSettings.main() ++
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
