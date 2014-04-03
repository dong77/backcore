import sbt._
import Keys._
import com.twitter.scrooge._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtScalariform._
import org.sbtidea.SbtIdeaPlugin._
import com.typesafe.sbt.SbtAtmos.{ Atmos, atmosSettings }
//import com.typesafe.sbt.SbtNativePackager._
//import NativePackagerKeys._

object CoinexBuild extends Build {
  val coinexVersion = "1.0.5-SNAPSHOT"
  val akkaVersion = "2.3.1"
  val bijectionVersion = "0.6.2"
  val sprayVersion = "1.3.1"
  val scroogeVersion = "3.13.0"

  val sharedSettings = Seq(
    organization := "com.coinport",
    version := coinexVersion,
    scalaVersion := "2.10.3",
    crossScalaVersions := Seq("2.10.3"),
    initialCommands in console := """
      import com.coinport.coinex.Client
      import com.coinport.coinex.data._
      import com.coinport.coinex.data.Currency._
      import com.coinport.coinex.robot.sample._
      import akka.pattern.ask
      import akka.util.Timeout
      import scala.concurrent.duration._

      implicit val timeout = Timeout(2 seconds)
    """,
    scalacOptions ++= Seq("-encoding", "utf8"),
    scalacOptions += "-deprecation",
    publishArtifact in Test := false,
    publishMavenStyle := true,
    publishTo := Some("Sonatype Snapshots Nexus" at "http://192.168.0.105:8081/nexus/content/repositories/snapshots"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      "Nexus Snapshots" at "http://192.168.0.105:8081/nexus/content/groups/public",
      "Spray Repo" at "http://repo.spray.io"
      // "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"
      ))

  lazy val root = Project(
    id = "coinex",
    base = file("."),
    settings = Project.defaultSettings ++ sharedSettings)
    .aggregate(client, backend)

  lazy val client = Project(
    id = "coinex-client",
    base = file("coinex-client"),
    settings = Project.defaultSettings ++
      sharedSettings ++
      ScroogeSBT.newSettings ++
      scalariformSettings
    )
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    .settings(libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
      "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
      "com.twitter" %% "bijection-scrooge" % bijectionVersion,
      "com.twitter" %% "bijection-json4s" % bijectionVersion,
      "com.twitter" %% "bijection-json" % bijectionVersion,
      "com.twitter" %% "scrooge-core" % scroogeVersion,
      "com.twitter" %% "scrooge-serializer" % scroogeVersion,
      "org.slf4s" %% "slf4s-api" % "1.7.6",
      "io.spray" %%  "spray-json" % "1.2.5",
      "org.json4s" %% "json4s-native" % "3.2.7",
      "com.google.guava" % "guava" % "16.0.1",
      "org.mongodb" %% "casbah" % "2.6.5",
      "com.twitter" %% "util-eval" % "6.12.1",
      "org.apache.thrift" % "libthrift" % "0.8.0"))

  lazy val backend = Project(
    id = "coinex-backend",
    base = file("coinex-backend"),
    settings = Project.defaultSettings ++
      SbtMultiJvm.multiJvmSettings ++
      sharedSettings ++
      ScroogeSBT.newSettings ++
      sbtassembly.Plugin.assemblySettings ++
      scalariformSettings
    )
    .settings(net.virtualvoid.sbt.graph.Plugin.graphSettings: _*)
    //.settings(packageArchetype.java_application:_*)
    //.settings(packageDescription in Debian := "coinex")
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-remote" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
        "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.7",
        "com.github.scullxbones" % "akka-persistence-mongo-casbah_2.10" % "0.0.4",
        "org.specs2" %% "specs2" % "2.3.8" % "test",
        "org.scalatest" %% "scalatest" % "2.0" % "test",
        "org.apache.commons" % "commons-lang3" % "3.1",
        "ch.qos.logback" % "logback-classic" % "1.0.13",
        "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.42",
        "io.spray" % "spray-can" % sprayVersion,
        "io.spray" % "spray-routing" % sprayVersion,
        "io.spray" % "spray-client" % sprayVersion,
        "io.spray" % "spray-http" % sprayVersion),
      
      compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
      parallelExecution in Test := false,
      executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
        case (testResults, multiNodeResults)  =>
          val overall =
            if (testResults.overall.id < multiNodeResults.overall.id)
              multiNodeResults.overall
            else
              testResults.overall
          Tests.Output(overall,
            testResults.events ++ multiNodeResults.events,
            testResults.summaries ++ multiNodeResults.summaries)
      }
    )
    .dependsOn(client)
    .configs(MultiJvm)
    
  lazy val integration = Project(
      id = "coinex-integration",
      base = file("coinex-integration"),
      settings = Project.defaultSettings ++
        sharedSettings ++
        scalariformSettings
      )
      .settings(
        libraryDependencies ++= Seq(
          "org.specs2" %% "specs2" % "2.3.8" % "test",
          "org.scalatest" %% "scalatest" % "2.0" % "test"
          )
      )
      .dependsOn(backend)
}
