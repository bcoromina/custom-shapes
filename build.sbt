
ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "shapes"

lazy val root = (project in file("."))
  .settings(
    commonSettings,
    name := "custom-shapes",
    libraryDependencies ++= Dependencies.Akka.All
  )


lazy val commonSettings = Seq(
  scalacOptions := Seq("-feature", "-unchecked", "-deprecation", "-encoding", "utf8", "-Ymacro-annotations"),
  Compile / packageDoc / mappings := Seq(),
  resolvers += "Akka library repository" at "https://repo.akka.io/maven",
  resolvers += "GitLab Maven" at "https://gitlab.com/api/v4/groups/78807844/-/packages/maven"
)