import sbt._
import Keys._

object MessagingPatternsBuild extends Build {

  private[this] val akkaVersion = "2.3.9"

  lazy val commonSettings = Seq(
    organization := "org.example",
//    scalacOptions := Seq(""),
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.11.5",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion
      , "com.typesafe.akka" %% "akka-remote" % akkaVersion
      , "com.github.scopt" %% "scopt" % "3.3.0"
    )
  )

  lazy val messagingPatterns = project.in(file("."))
    .settings(commonSettings: _*)
    .settings(name := "Messaging patterns")

}
