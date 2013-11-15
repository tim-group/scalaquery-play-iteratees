// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository
resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.0.8")

// Visualize all dependencies of this project
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")
