// import sbtprotobuf.{ProtobufPlugin=>PB}

name := "coinex"

version := "1.0"

fork := true

scalaVersion := "2.10.3"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= {
  val akkaVersion = "2.3.0-RC1"
  val akkaModules = Seq("contrib", "cluster", "agent", "remote", "persistence-experimental")
  Seq(
    "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.7",
    "com.github.ddevore" %% "akka-persistence-mongo" % "0.2-SNAPSHOT",
    // "com.google.protobuf" % "protobuf-java" % "2.5.0",
    // "org.scala-lang" %% "scala-pickling" % "0.8.0-SNAPSHOT",
    "org.scalatest" % "scalatest_2.10" % "1.9.1" % "test"
  ) ++ akkaModules.map{m => "com.typesafe.akka" %% ("akka-" + m) % akkaVersion}
}

// seq(PB.protobufSettings: _*)