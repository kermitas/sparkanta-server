object Build extends sbt.Build {

  lazy final val version     = "0.1.0-SNAPSHOT"

  // --- projects definition

  lazy val scalaUtils     = SparkantaScalaUtilsProject(version)
  lazy val akkaUtils      = SparkantaAkkaUtilsProject(version)

  lazy val common         = SparkantaCommonProject(version, scalaUtils)
  lazy val tcpRestGateway = SparkantaTcpRestGatewayProject(version, common, akkaUtils)
  lazy val restServer     = SparkantaRestServerProject(version, common)

  lazy val sparkanta      = SparkantaProject(version, tcpRestGateway, restServer)
}
