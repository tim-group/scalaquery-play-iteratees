name := "scalaquery-play-iteratees"

organization := "com.timgroup"

// Change to non-SNAPSHOT version to publish a release
version := "1.1.1"

scalaVersion := "2.10.3"

playVersion := "2.2.1"

crossScalaVersions := Seq("2.10.3")

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % playVersion.value intransitive(),
  "com.typesafe.play" %% "play-iteratees" % playVersion.value intransitive(),
  "com.typesafe.slick" %% "slick" % "2.0.0" intransitive(),
  "com.h2database" % "h2" % "1.3.166" intransitive(),
  "joda-time" % "joda-time" % "2.3" intransitive(),
  "org.joda" % "joda-convert" % "1.5" intransitive(),
  "org.slf4j" % "slf4j-api" % "[1.6.6]" force(),
  // Test-only dependencies
  "org.scalatest" %% "scalatest" % "2.0" % "test",
  "org.mockito" % "mockito-core" % "1.9.0" % "test",
  "com.typesafe.slick" %% "slick-testkit" % "2.0.0" % "test",
  "ch.qos.logback" % "logback-classic" % "1.0.13" % "test"
)

publishMavenStyle := true

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := (
  <url>https://github.com/youdevise/scalaquery-play-iteratees</url>
  <licenses>
    <license>
      <name>MIT license</name>
      <url>http://www.opensource.org/licenses/mit-license.php</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>git@github.com:youdevise/scalaquery-play-iteratees.git</url>
    <connection>scm:git:git@github.com:youdevise/scalaquery-play-iteratees.git</connection>
  </scm>
  <developers>
    <developer>
      <id>ms-tg</id>
      <name>Marc Siegel</name>
      <email>marc.siegel@timgroup.com</email>
    </developer>
  </developers>
)

// NOTE (2013-04-23, Marc): To publish to Sonatype:
//
// 1. Install sbt-extras as ~/bin/sbt-extras.sh, to handle multiple versions of sbt gracefully
//      https://github.com/paulp/sbt-extras
//
// 2. Generate and publish your GPG key
//      https://docs.sonatype.org/display/Repository/How+To+Generate+PGP+Signatures+With+Maven
//
// 3. Setup sbt-pgp plugin
//      ~/.sbt/0.12.3/plugins/gpg.sbt:
//          addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8")
//
// 4. Setup local Sonatype credentials
//      ~/.sbt/0.12.3/plugins/sonatype.sbt:
//          credentials += Credentials("Sonatype Nexus Repository Manager",
//                                     "oss.sonatype.org",
//                                     USERNAME,
//                                     PASSWORD)
//
// 5. Bump version
//       For snapshot versions: 1.0.0-SNAPSHOT
//       For releases: 1.0.0
//
// 6. Deploy and Stage to Sonatype
//       sbt-extras.sh clean publish-signed            // scalaVersion as configured above
//       sbt-extras.sh ++2.9.2 clean publish-signed    // scalaVersion given on command line
//
// 7. Manually release it to Maven Central
//       https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide#SonatypeOSSMavenRepositoryUsageGuide-8a.ReleaseIt

// NOTE: If you have multiple GPG keys, and it is signing with the wrong one, you must
// do additional steps:
//
//   2.a. List your GPG keys to identify the one you want to sign with
//          gpg --list-keys
//
//   2.b. Edit ~/.gnupg/gpg.conf to set the default-key to the desired key id
//
//   2.c. Launch gpg-agent in your active terminal
//          eval $(gpg-agent --daemon)
//
//   2.d. Uncomment the following line so that SBT will use the GPG app (and its settings)
//useGpg := true
