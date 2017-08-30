name := "dataFusion-db-service"

libraryDependencies ++= Seq(
  "com.github.swagger-akka-http" %% "swagger-akka-http" % "0.9.1",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.7",
  "ch.megard" %% "akka-http-cors" % "0.2.1",
  "com.github.scopt" %% "scopt" % "3.5.0"
)

com.github.retronym.SbtOneJar.oneJarSettings

mainClass in Compile := Some("au.csiro.data61.dataFusion.db.service.Main")
