import sbt._

object Dependencies {

  object Version {
    val akka = "2.4.11"
  }

  lazy val actors = akka
  lazy val streams = akka ++ akkaStreams

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % Version.akka,
    "com.typesafe.akka" %% "akka-testkit" % Version.akka % Test
  )

  val akkaStreams = Seq(
    "com.typesafe.akka" %% "akka-stream" % Version.akka
  )

}