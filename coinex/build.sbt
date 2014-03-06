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

libraryDependencies ++= {
  val akkaVersion = "2.3.0-RC1"
  val sprayVersion = "1.3-RC1"
  Seq(
    "org.scalatest"            %  "scalatest_2.10"                   % "1.9.1" % "test",
    "com.typesafe.akka"        %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"        %% "akka-cluster"                     % akkaVersion,
    "com.typesafe.akka"        %% "akka-slf4j"                       % akkaVersion,
    "com.typesafe.akka"        %% "akka-remote"                      % akkaVersion,
    "com.typesafe.akka"        %% "akka-contrib"                     % akkaVersion,
    "com.typesafe.akka"        %% "akka-persistence-experimental"    % akkaVersion,
    "io.spray"                 %% "spray-json"                       % "1.2.5",
    "org.fusesource.leveldbjni"   %  "leveldbjni-all"                % "1.7",
    "com.github.ddevore" %%       "akka-persistence-mongo-casbah"    % "0.4-SNAPSHOT",
    "org.specs2"                  %% "specs2"                        % "2.3.8" % "test"
  )
}

// seq(PB.protobufSettings: _*)
