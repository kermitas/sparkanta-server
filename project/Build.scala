object Build extends sbt.Build {

  lazy final val version     = "0.1.0-SNAPSHOT"

  // --- projects definition

  lazy val scalaUtils              = ScalaUtilsProject(version)

  lazy val sparkantaApi            = SparkantaApiProject(version, scalaUtils)
  lazy val sparkantaTcpRestGateway = SparkantaTcpRestGatewayProject(version, sparkantaApi)
  lazy val sparkantaRestServer     = SparkantaRestServerProject(version, sparkantaApi)

  lazy val sparkanta               = SparkantaProject(version, sparkantaTcpRestGateway, sparkantaRestServer)
}
