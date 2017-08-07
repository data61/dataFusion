name := "dataFusion-search"

libraryDependencies ++= Seq(
  "lucene-core",
  "lucene-analyzers-common",
  "lucene-queryparser",
  "lucene-highlighter"
).map("org.apache.lucene" % _ % "6.5.0")

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.1",
  "com.github.scopt" %% "scopt" % "3.5.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)

com.github.retronym.SbtOneJar.oneJarSettings

mainClass in Compile := Some("au.csiro.data61.dataFusion.search.Main")
