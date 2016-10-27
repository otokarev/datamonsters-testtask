lazy val root = (project in file("."))
  .settings(
    name := "datamonsters-testtask"
  ).aggregate(
    actors,
    streams
  )

val commonSettings = Seq(
  organization := "otokarev@gmail.com",
  version := "0.1.0",
  scalaVersion := "2.11.8"
)

lazy val actors = (project in file("actors"))
  .settings(
    name := "datamonsters-testtask-actors",
    libraryDependencies ++= Dependencies.actors,
    fork in run := true,
    commonSettings
  )

lazy val streams = (project in file("streams"))
  .settings(
    name := "datamonsters-testtask-streams",
    libraryDependencies ++= Dependencies.streams,
    fork in run := true,
    commonSettings
  )

fork in run := true