name := "dataFusion-tika-service"

// the one-jar classloader helpfully reports on conflicting classes (same package & name) from different jars
// (including whether the byte-code differs) and this has been used to set the following exclusions:

libraryDependencies ++= Seq(
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.9.1" exclude("javax.ws.rs", "jsr311-api"), // replaced by javax.ws.rs-api
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.7",
  "ch.megard" %% "akka-http-cors" % "0.2.1",
  
  "com.github.scopt" %% "scopt" % "3.7.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test"
)
  
com.github.retronym.SbtOneJar.oneJarSettings

mainClass in Compile := Some("au.csiro.data61.dataFusion.tika.service.Main")
