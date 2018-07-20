name := "dataFusion-db"

libraryDependencies ++= Seq(
  "slick",
  "slick-hikaricp",
  "slick-codegen"
).map("com.typesafe.slick" %% _ % "3.2.1")

libraryDependencies ++= Seq(
  "org.postgresql" % "postgresql" % "42.1.4",
  "com.h2database" % "h2" % "1.4.196",
  "com.typesafe" % "config" % "1.3.1",
  "com.github.scopt" %% "scopt" % "3.7.0",
  "com.jsuereth" %% "scala-arm" % "2.0"
  
)

com.github.retronym.SbtOneJar.oneJarSettings

mainClass in Compile := Some("au.csiro.data61.dataFusion.db.Main")
