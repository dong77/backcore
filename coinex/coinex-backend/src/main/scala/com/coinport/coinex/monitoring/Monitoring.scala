/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.monitoring

import scala.concurrent.duration._
import akka.actor._
import akka.util.Timeout
import scala.util.{ Success, Failure }
import akka.pattern._
import spray.routing._
import spray.routing.directives._
import spray.can.Http
import spray.httpx.marshalling.Marshaller
import spray.httpx.encoding.Gzip
import spray.util._
import spray.http._
import MediaTypes._
import HttpHeaders._
import reflect.ClassTag
import org.json4s._
import native.Serialization.{ read, write => swrite }
import com.coinport.coinex.LocalRouters
import com.coinport.coinex.data._
import akka.cluster.Cluster
import akka.actor.Terminated
import com.typesafe.config.Config

/**
 * TODO(d): finish this class.
 */
class Monitor(actorPaths: List[String], mailer: ActorRef, config: Config)(implicit cluster: Cluster)
    extends Actor with HttpService with spray.httpx.SprayJsonSupport with ActorLogging {
  val actorRefFactory = context
  implicit def executionContext = context.dispatcher
  implicit val formats = native.Serialization.formats(NoTypeHints)
  implicit val timeout: Timeout = 1 second

  override def preStart = {
    super.preStart()
    watchAllActor
  }

  def receive = runRoute(route) orElse {
    case Terminated(actor) => {
      log.error("[ERROR]ACTOR WAS TERMINATED >>>> " + actor.toString)
      sendMonitorEmail("[ERROR]ACTOR WAS TERMINATED >>>> " + actor.toString)
    }
  }

  def sendMonitorEmail(content: String) {
    mailer ! DoSendEmail(config.getString("akka.exchange.monitor.mail.address"), EmailType.Monitor, Map("CONTENT" -> content))
  }
  def watchAllActor() {
    for (path <- actorPaths; clusterMember <- cluster.state.members) {
      val f = cluster.system.actorSelection(clusterMember.address.toString + path).resolveOne(2 seconds)
      f onSuccess {
        case m => {
          log.debug("watch actor >>>>>>> " + m)
          context.watch(m)
        }
      }
      f onFailure { case m => log.warning("unknow actor path >>>> " + clusterMember.address.toString + path) }
    }
  }

  val route: Route = {
    get {
      pathSingleSlash {
        val lists = actorPaths.map { path =>
          "<li><a href=\"/actor/stats?path=%s\">%s</a></li>".format(path, path)
        }.mkString

        val html = "<html><body><ui>" + lists + "</ui></body></html>"
        respondWithMediaType(`text/html`) { complete(html) }

      } ~ path("actor" / "stats") {
        parameter("path") { path =>
          respondWithMediaType(`application/json`) {
            onComplete(context.actorSelection(path) ? QueryActorStats) {
              case Success(stats: AnyRef) => complete(swrite(stats))
              case Success(v) => complete("" + v)
              case Failure(e) => failWith(e)
            }
          }
        }
      } ~ path("actor" / "dumpstate") {
        parameter("path") { path =>
          respondWithMediaType(`application/json`) {
            onComplete(context.actorSelection(path) ? DumpStateToFile(path)) {
              case Success(file: String) => complete(swrite(file))
              case Success(v) => complete("" + v)
              case Failure(e) => failWith(e)
            }
          }
        }
      } ~ path("config") {
        complete("TODO")
      }
    }
  }
}
