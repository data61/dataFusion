name := "dataFusion-common"

libraryDependencies ++= Seq(
  "io.spray" %% "spray-json" % "1.3.3",
  // "io.swagger" % "swagger-annotations" % "1.5.12",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.0.3" % "test"
)
