name := "demo-client"


scalaVersion := "2.11.1"


libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.2",
  "com.typesafe.akka" %% "akka-remote" % "2.3.2",
  "com.persist" %% "persist-json" % "0.21",
  "jline" % "jline" % "2.12"
)



