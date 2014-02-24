// import sbtprotobuf.{ProtobufPlugin=>PB}
import spray.revolver.RevolverPlugin.Revolver

name := "coinex"

version := "1.0"

scalaVersion := "2.10.3"

resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    "spray repo" at "http://repo.spray.io",
    "spray nightlies repo" at "http://nightlies.spray.io"
)

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
    "com.github.ddevore"       %% "akka-persistence-mongo-casbah"    % "0.3-SNAPSHOT",
    "io.spray"                 %  "spray-io"                         % sprayVersion,
    "io.spray"                 %  "spray-can"                        % sprayVersion,
    "io.spray"                 %  "spray-routing"                    % sprayVersion,
    "io.spray"                 %  "spray-http"                       % sprayVersion,
    "io.spray"                 %  "spray-httpx"                      % sprayVersion,
    "io.spray"                 %  "spray-client"                     % sprayVersion,
    "io.spray"                 %  "spray-caching"                    % sprayVersion,
    "io.spray"                 %  "spray-servlet"                    % sprayVersion,
    "io.spray"                 %  "spray-util"                       % sprayVersion,
    "io.spray"                 %% "spray-json"                       % "1.2.5",
    "org.fusesource.leveldbjni"  %  "leveldbjni-all"                 % "1.7"
    // "com.google.protobuf"     %  "protobuf-java"                  % "2.5.0"
  )
}

// seq(PB.protobufSettings: _*)
