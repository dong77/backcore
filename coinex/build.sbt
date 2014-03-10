import AssemblyKeys._

name := "coinex"

organization := "com.coinport"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.3"

resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    "Nexus Snapshots" at "http://192.168.0.105:8081/nexus/content/repositories/snapshots/"
    // "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"
)


libraryDependencies ++= {
  val akkaVersion = "2.3.0"
  Seq(
    "com.typesafe.akka"           %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"           %% "akka-cluster"                     % akkaVersion,
    "com.typesafe.akka"           %% "akka-slf4j"                       % akkaVersion,
    "com.typesafe.akka"           %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"           %% "akka-contrib"                     % akkaVersion,
    "com.typesafe.akka"           %% "akka-persistence-experimental"    % akkaVersion,
    "com.typesafe.akka"           %% "akka-testkit"                     % akkaVersion,
    "org.fusesource.leveldbjni"   %  "leveldbjni-all"                   % "1.7",
    "com.github.ddevore"          %% "akka-persistence-mongo-casbah"    % "0.4-SNAPSHOT",
    "org.specs2"                  %% "specs2"                           % "2.3.8" % "test",
    "org.scalatest"               %  "scalatest_2.10"                   % "1.9.1" % "test",
    "org.apache.commons"          %  "commons-lang3"                    % "3.1"
  )
}

// seq(ScctPlugin.instrumentSettings : _*)

assemblySettings

// net.leifwarner.SbtGitInfo.setting

publishTo := Some("Sonatype Snapshots Nexus" at "http://192.168.0.105:8081/nexus/content/repositories/snapshots")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
