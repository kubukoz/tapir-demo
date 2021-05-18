libraryDependencies ++= Seq(
  "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "0.17.19",
  "com.softwaremill.sttp.tapir" %% "tapir-cats" % "0.17.19",
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "0.17.19",
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % "0.17.19",
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % "0.17.19",
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-akka-http" % "0.17.19",
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-model" % "0.17.19",
  "org.typelevel" %% "cats-effect" % "2.5.1"
)

scalaVersion := "2.13.5"
