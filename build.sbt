enablePlugins(ScalaJSPlugin, BuildInfoPlugin)

name := "my_app"

version := "0.0.1"

scalaVersion := "2.12.8"

resolvers += Resolver.jcenterRepo
resolvers += Resolver.bintrayRepo("neelsmith", "maven")
resolvers += Resolver.bintrayRepo("eumaeus", "maven")
resolvers += sbt.Resolver.bintrayRepo("denigma", "denigma-releases")

libraryDependencies ++= Seq(
  "org.scala-js" %% "scalajs-stubs" % scalaJSVersion % "provided",
  "org.scala-js" %%% "scalajs-dom" % "0.9.5",
  "io.monix" %%% "monix" % "2.3.0",
  "edu.holycross.shot.cite" %%% "xcite" % "3.7.0",
  "edu.holycross.shot" %%% "ohco2" % "10.16.0",
  "edu.holycross.shot" %%% "scm" % "7.0.1",
  "edu.holycross.shot" %%% "citeobj" % "7.3.4",
  "edu.holycross.shot" %%% "citerelations" % "2.5.2",
  "edu.holycross.shot" %%% "cex" % "6.3.3",
  "edu.furman.classics" %%% "citealign" % "0.5.0",
  "com.thoughtworks.binding" %%% "dom" % "latest.version"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

//scalacOptions += "-P:scalajs:suppressExportDeprecations"
//scalacOptions += "-P:scalajs:suppressMissingJSGlobalDeprecations"
scalacOptions += "-unchecked"
scalacOptions += "-deprecation"

import scala.io.Source
import java.io.PrintWriter

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "citewidgets"
