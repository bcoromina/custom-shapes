import sbt._

object Dependencies {
  object Akka{
    val akkaVer = "2.9.0"

    val streamTyped = "com.typesafe.akka" %% "akka-stream-typed" % akkaVer

    val All: Seq[ModuleID] = Seq(streamTyped)
  }
}
