import sbt._
import Keys._

object ScalaQueryPlayIterateesBuild extends Build {
  val playVersion = SettingKey[String]("play-version", "The version of Play Framework used for building.")
}