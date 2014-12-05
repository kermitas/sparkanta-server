object Build extends sbt.Build {

  lazy final val version     = "0.1.0-SNAPSHOT"

  // --- projects definition

  lazy val scalaUtils     = SparkantaScalaUtilsProject(version)
  lazy val akkaUtils      = SparkantaAkkaUtilsProject(version)

  lazy val api            = SparkantaApiProject(version, scalaUtils)
  lazy val tcpRestGateway = SparkantaTcpRestGatewayProject(version, api, akkaUtils)
  lazy val restServer     = SparkantaRestServerProject(version, api)

  lazy val sparkanta      = SparkantaProject(version, tcpRestGateway, restServer)
}
