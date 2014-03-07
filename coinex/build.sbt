// import sbtprotobuf.{ProtobufPlugin=>PB}
import spray.revolver.RevolverPlugin.Revolver

name := "coinex"

version := "1.0"

scalaVersion := "2.10.3"

resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots")
)

// https://github.com/scullxbones/akka-persistence-mongo/
// 

com.twitter.scrooge.ScroogeSBT.newSettings

libraryDependencies ++= {
  val akkaVersion = "2.3.0"
  Seq(
    "com.twitter"                 %% "scrooge-core"                     % "3.12.3",
    "org.apache.thrift"           %  "libthrift"                        % "0.8.0",
    "org.scalatest"               %  "scalatest_2.10"                   % "1.9.1" % "test",
    "com.typesafe.akka"           %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"           %% "akka-cluster"                     % akkaVersion,
    "com.typesafe.akka"           %% "akka-slf4j"                       % akkaVersion,
    "com.typesafe.akka"           %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"           %% "akka-contrib"                     % akkaVersion,
    "com.typesafe.akka"           %% "akka-persistence-experimental"    % akkaVersion,
		"com.typesafe.akka"           %% "akka-testkit"                     % akkaVersion,
    "io.spray"                    %% "spray-json"                       % "1.2.5",
    "org.fusesource.leveldbjni"   %  "leveldbjni-all"                   % "1.7",
    "com.github.ddevore"          %% "akka-persistence-mongo-casbah"    % "0.4-SNAPSHOT",
    "org.specs2"                  %% "specs2"                           % "2.3.8" % "test"
  )
}
