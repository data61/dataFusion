name := "dataFusion-ner"

libraryDependencies ++= Seq(
  "edu.stanford.nlp" % "stanford-corenlp" % "3.8.0" withSources(),
  "edu.stanford.nlp" % "stanford-corenlp" % "3.8.0" classifier "models" classifier "models-spanish",
  "org.apache.opennlp" % "opennlp-tools" % "1.8.1",
  // "com.google.protobuf" % "protobuf-java" % "3.1.0", // undeclared dependency of corenlp?
  "com.typesafe" % "config" % "1.3.1",
  "com.github.scopt" %% "scopt" % "3.5.0",
  "com.jsuereth" %% "scala-arm" % "2.0",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)

com.github.retronym.SbtOneJar.oneJarSettings

mainClass in Compile := Some("au.csiro.data61.dataFusion.ner.Main")
