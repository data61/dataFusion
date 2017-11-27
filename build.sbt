import ReleaseTransformations._
import com.typesafe.sbt.license.{DepModuleInfo, LicenseInfo}

// default release process, but without publishArtifacts
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  // publishArtifacts,
  setNextVersion,
  commitNextVersion,
  pushChanges
)

def hasPrefix(org: String, prefixes: Seq[String]) = prefixes.exists(x => org.startsWith(x))

lazy val commonSettings = Seq(
  organization := "au.csiro.data61",
  // version := "0.1-SNAPSHOT", // see version.sbt maintained by sbt-release plugin
  licenses := Seq("GPL" -> url("https://www.gnu.org/licenses/gpl-3.0.en.html")),
  homepage := Some(url("https://github.com/NICTA/dataFusion")),

  scalaVersion := "2.12.2",
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
  exportJars := true, // required by sbt-onejar
  autoAPIMappings := true, // scaladoc
  
  // unmanagedSourceDirectories in Compile := (scalaSource in Compile).value :: Nil, // only Scala sources, no Java
  unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil,
  
  // filterScalaLibrary := false, // sbt-dependency-graph: include scala library in output
  scalacOptions in (Compile,doc) := Seq("-diagrams"), // sbt-dependency-graph needs: sudo apt-get install graphviz
  
  EclipseKeys.withSource := true,
  // If Eclipse and sbt are both building to same dirs at same time it takes forever and produces corrupted builds.
  // So here we tell Eclipse to build somewhere else (bin is it's default build output folder)
  EclipseKeys.eclipseOutput in Compile := Some("bin"),   // default is sbt's target/scala-2.11/classes
  EclipseKeys.eclipseOutput in Test := Some("test-bin"), // default is sbt's target/scala-2.11/test-classes

  licenseOverrides := {
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("org.apache", "com.fasterxml", "com.google.guava", "org.javassist", "io.swagger", "org.json4s", "xalan", "commons-codec", "commons-logging", "regexp")) => LicenseInfo(LicenseCategory.Apache, "The Apache Software License, Version 2.0", "http://www.apache.org/licenses/LICENSE-2.0.txt")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("com.thoughtworks.paranamer")) => LicenseInfo(LicenseCategory.BSD, "BSD-Style", "http://www.opensource.org/licenses/bsd-license.php")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("javax.ws.rs", "org.jvnet.mimepull", "org.glassfish")) => LicenseInfo(LicenseCategory.GPLClasspath, "CDDL + GPLv2 with classpath exception", "https://glassfish.dev.java.net/nonav/public/CDDL+GPL.html")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("ch.qos.logback")) => LicenseInfo(LicenseCategory.LGPL, "EPL + GNU Lesser General Public License", "http://logback.qos.ch/license.html")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("com.google.code.findbugs")) => LicenseInfo(LicenseCategory.LGPL, "GNU Lesser General Public License", "http://www.gnu.org/licenses/lgpl.html")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("org.slf4j")) => LicenseInfo(LicenseCategory.MIT, "MIT License", "http://www.slf4j.org/license.html")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("org.bouncycastle")) => LicenseInfo(LicenseCategory.MIT, "Bouncy Castle Licence (MIT)", "http://www.bouncycastle.org/licence.html")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("org.opengis")) => LicenseInfo(LicenseCategory.BSD, "OGC copyright (BSD 2 clause)", "https://svn.code.sf.net/p/geoapi/code/branches/3.0.x/LICENSE.txt")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("javax.measure")) => LicenseInfo(LicenseCategory.BSD, "BSD-Style", "http://www.opensource.org/licenses/bsd-license.php")
    case DepModuleInfo(org, _, _) if hasPrefix(org, Seq("net.jcip")) => LicenseInfo(LicenseCategory.CC0, "Creative Commons Attribution License", "http://creativecommons.org/licenses/by/2.5")
    }
  )

// the sbt build honours transitive dependsOn, however this is not honoured by sbteclipse-plugin,
// so we need to explicitly add dependsOn for each transitive dependency

unmanagedSourceDirectories := Nil // no sources in top level project

lazy val common = (project in file("dataFusion-common")).
  settings(commonSettings: _*)

lazy val tika = (project in file("dataFusion-tika")).
  dependsOn(common).
  settings(commonSettings: _*)
  
lazy val tikaService = (project in file("dataFusion-tika-service")).
  dependsOn(common).
  dependsOn(tika).
  settings(commonSettings: _*)
  
lazy val db = (project in file("dataFusion-db")).
  dependsOn(common).
  settings(commonSettings: _*)

lazy val dbService = (project in file("dataFusion-db-service")).
  dependsOn(common).
  dependsOn(db).
  settings(commonSettings: _*)

lazy val ner = (project in file("dataFusion-ner")).
  dependsOn(common).
  settings(commonSettings: _*)

lazy val nerService = (project in file("dataFusion-ner-service")).
  dependsOn(common).
  dependsOn(ner).
  settings(commonSettings: _*)
  
lazy val search = (project in file("dataFusion-search")).
  dependsOn(common).
  settings(commonSettings: _*)

lazy val searchService = (project in file("dataFusion-search-service")).
  dependsOn(common).
  dependsOn(search).
  settings(commonSettings: _*)
    
lazy val util = (project in file("dataFusion-util")).
  dependsOn(common).
  dependsOn(search).
  settings(commonSettings: _*)
  
lazy val graphService = (project in file("dataFusion-graph-service")).
  dependsOn(common).
  settings(commonSettings: _*)

