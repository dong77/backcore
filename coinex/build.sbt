name := "coinex"

version := "1.0"

fork := true

scalaVersion := "2.10.2"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= {
  val akkaVersion = "2.3.0-RC1"
  val akkaModules = Seq("contrib", "cluster", "agent", "remote", "persistence-experimental")
  Seq(
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.7",
    "com.github.ddevore" %% "akka-persistence-mongo" % "0.2-SNAPSHOT",
    "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"
  ) ++ akkaModules.map{m => "com.typesafe.akka" %% ("akka-" + m) % akkaVersion}
}