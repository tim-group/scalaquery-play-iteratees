import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {
  val appName = "scalaquery-play-iteratees-sample"
  val appVersion = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "org.scalaquery" %% "scalaquery" % "0.10.0-M1"
  )

  // Depend on the latest local code of the play module we're testing
  val module = RootProject(file("../module"))

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA)
    .dependsOn(module)
}
