import sbt._
import Keys._

object SparkantaServerProject {

  lazy final val projectName = SparkantaProject.projectName + "-server"
  lazy final val mainClassFullyQualifiedName = "as.sparkanta.server.Main"

  def apply(version: String, sparkantaApi: Project) =
    Project(
      id           = projectName,
      base         = file(projectName),

      aggregate    = Seq(sparkantaApi),
      dependencies = Seq(sparkantaApi),
      delegates    = Seq(sparkantaApi),

      settings     = CommonSettings(projectName, version) ++
                     ScalaTestSettings() ++
                     AkkaSettings() ++
                     Slf4jSettings() ++
                     mainClassSettings(mainClassFullyQualifiedName) ++
                     PackSettings(mainClassFullyQualifiedName) ++
                     AssemblySettings(mainClassFullyQualifiedName)
    )

  protected def mainClassSettings(mainClassFullyQualifiedName: String) = Seq (
    mainClass := Some(mainClassFullyQualifiedName)
  )
}
