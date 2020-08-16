name := "akka-basics"

version := "0.1"

scalaVersion := "2.12.7"

lazy val akkaVersion = "2.5.13"
lazy val scalaTestVersion = "3.0.5"

libraryDependencies ++= Seq(

  // Akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  
  // Testing
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion
)