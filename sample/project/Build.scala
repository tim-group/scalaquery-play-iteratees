import sbt._
import Keys._
import play.Project._

object ApplicationBuild extends Build {
  val appName = "scalaquery-play-iteratees-sample"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    jdbc,
    "com.typesafe.slick" %% "slick" % "1.0.1",
    "com.typesafe.play" %% "play-slick" % "0.5.0.8"
  )

  // Depend on the latest local code of the play module we're testing
  val module = RootProject(file("../module"))

  val main = play.Project(appName, appVersion, appDependencies)
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .dependsOn(module)
}
