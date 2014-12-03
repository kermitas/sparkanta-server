import sbt._
import Keys._

object SparkantaServerProject {

  lazy final val projectName                 = SparkantaProject.projectName + "-server"
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
                     Slf4jSettings() ++
                     mainClassSettings(mainClassFullyQualifiedName) ++
                     PackSettings(mainClassFullyQualifiedName) ++
                     AssemblySettings(mainClassFullyQualifiedName)
    )

  protected def mainClassSettings(mainClassFullyQualifiedName: String) = Seq (
    mainClass in (Compile,run) := Some(mainClassFullyQualifiedName)
  )
}
