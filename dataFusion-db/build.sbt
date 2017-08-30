name := "dataFusion-db"

libraryDependencies ++= Seq(
  "slick",
  "slick-hikaricp",
  "slick-codegen"
).map("com.typesafe.slick" %% _ % "3.2.0")

libraryDependencies ++= Seq(
  "au.csiro.data61" %% "datafusion-common" % "0.2-SNAPSHOT",
  "org.postgresql" % "postgresql" % "42.1.1",
  "com.h2database" % "h2" % "1.4.195"
)

com.github.retronym.SbtOneJar.oneJarSettings

mainClass in Compile := Some("au.csiro.data61.dataFusion.db.Main")
