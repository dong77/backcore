import AssemblyKeys._
import spray.revolver.RevolverPlugin.Revolver

name := "coinex"

version := "1.0"

scalaVersion := "2.10.3"

resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots")
)

com.twitter.scrooge.ScroogeSBT.newSettings

libraryDependencies ++= {
  val akkaVersion = "2.3.0"
  val bijectionVersion = "0.6.2"
  Seq(
    "com.typesafe.akka"           %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"           %% "akka-cluster"                     % akkaVersion,
    "com.typesafe.akka"           %% "akka-slf4j"                       % akkaVersion,
    "com.typesafe.akka"           %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"           %% "akka-contrib"                     % akkaVersion,
    "com.typesafe.akka"           %% "akka-persistence-experimental"    % akkaVersion,
    "com.typesafe.akka"           %% "akka-testkit"                     % akkaVersion,
    "com.twitter"                 %% "scrooge-core"                     % "3.12.3",
    "com.twitter"                 %% "scrooge-serializer"               % "3.12.3",
    "org.apache.thrift"           %  "libthrift"                        % "0.8.0",
    "org.fusesource.leveldbjni"   %  "leveldbjni-all"                   % "1.7",
    "com.github.ddevore"          %% "akka-persistence-mongo-casbah"    % "0.4-SNAPSHOT",
    "com.twitter"                 %% "bijection-core"                   % bijectionVersion,
    "com.twitter"                 %% "bijection-thrift"                 % bijectionVersion,
    "com.twitter"                 %% "bijection-json"                   % bijectionVersion,
    "com.twitter"                 %% "bijection-hbase"                  % bijectionVersion,
    "com.twitter"                 %% "bijection-scrooge"                % bijectionVersion,
    "org.specs2"                  %% "specs2"                           % "2.3.8" % "test",
    "org.scalatest"               %  "scalatest_2.10"                   % "1.9.1" % "test",
    "org.apache.commons"          %  "commons-lang3"                    % "3.1"
  )
}

seq(ScctPlugin.instrumentSettings : _*)

assemblySettings

net.leifwarner.SbtGitInfo.setting
