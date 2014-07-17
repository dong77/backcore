name := """bitway2"""

version := "1.0"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "Nexus Snapshots" at "http://192.168.0.105:8081/nexus/content/groups/public/",
  "Nexus Thirdparty" at "http://192.168.0.105:8081/nexus/content/repositories/thirdparty/",
  "jahia org repository" at "http://maven.jahia.org/maven2/"
)

libraryDependencies ++= {
  Seq(
	"com.typesafe.akka"		%% "akka-actor"		% "2.3.3",
	"redis.clients"			% "jedis"		% "2.4.2",
	"net.databinder.dispatch" 	%% "dispatch-core" 	% "0.11.1",
        "org.mongodb" 			%% "casbah" 		% "2.6.5",
	"com.coinport"         		%% "coinex-client"	% "1.1.32-SNAPSHOT",
	"org.scalatest" 		%% "scalatest" 		% "2.0" 	% "test"
  )
}
