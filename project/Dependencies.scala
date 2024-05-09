import sbt._

object Dependencies {
  object Akka{
    val akkaVer = "2.9.0"

    val streamTyped = "com.typesafe.akka" %% "akka-stream-typed" % akkaVer


    val streamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaVer % Test
    val actorTestkitTyped = "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVer % Test
    val slf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVer

    val All: Seq[ModuleID] = Seq(streamTyped, streamTestkit, actorTestkitTyped, slf4j)
  }

  object ScalaTest {

    val scalatestVer = "3.2.16"

    val scalatest = "org.scalatest" %% "scalatest" % scalatestVer % Test
    val shouldmatchers = "org.scalatest" %% "scalatest-shouldmatchers" % scalatestVer % Test
    val wordspec = "org.scalatest" %% "scalatest-wordspec" % scalatestVer % Test
    val flatspec = "org.scalatest" %% "scalatest-flatspec" % scalatestVer % Test
    val scalacheck = "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test

    val All: Seq[ModuleID] = Seq(scalatest, shouldmatchers, wordspec, flatspec, scalacheck)
  }

  object Logging {

    val logback = "ch.qos.logback" % "logback-classic" % "1.4.12"
    val All: Seq[ModuleID] = Seq(logback)
  }
}
