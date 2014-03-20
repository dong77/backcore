import sbt._
import Keys._
import com.twitter.scrooge._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtScalariform._

object CoinexBuild extends Build {
  val akkaVersion = "2.3.0"
  val bijectionVersion = "0.6.2"

  val sharedSettings = Seq(
    organization := "com.coinport",
    version := "1.0.0-SNAPSHOT",
    scalaVersion := "2.10.3",
    crossScalaVersions := Seq("2.10.3"),
    scalacOptions ++= Seq("-encoding", "utf8"),
    scalacOptions += "-deprecation",
    publishArtifact in Test := false,
    publishMavenStyle := true,
    publishTo := Some("Sonatype Snapshots Nexus" at "http://192.168.0.105:8081/nexus/content/repositories/snapshots"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    resolvers ++= Seq(
      Resolver.sonatypeRepo("snapshots"),
      "Nexus Snapshots" at "http://192.168.0.105:8081/nexus/content/groups/public" // "scct-github-repository" at "http://mtkopone.github.com/scct/maven-repo"
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
      "com.typesafe.akka" %% "akka-persistence-experimental" % akkaVersion,
      "com.twitter" %% "bijection-scrooge" % bijectionVersion,
      "com.twitter" %% "scrooge-core" % "3.12.3",
      "com.twitter" %% "scrooge-serializer" % "3.12.3",
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
    .settings(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-remote" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
        "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
        "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
        "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
        "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.7",
        "com.github.scullxbones" % "akka-persistence-mongo-casbah_2.10" % "0.0.4",
        "org.specs2" %% "specs2" % "2.3.8" % "test",
        "org.scalatest" %% "scalatest" % "2.0" % "test",
        "org.apache.commons" % "commons-lang3" % "3.1",
        "ch.qos.logback" % "logback-classic" % "1.0.13"),
      // make sure that MultiJvm test are compiled by the default test compilation
      compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
      // disable parallel tests
      parallelExecution in Test := false,
      // make sure that MultiJvm tests are executed by the default test target, 
      // and combine the results from ordinary test and multi-jvm tests
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
    .dependsOn(client) configs (MultiJvm)
}
