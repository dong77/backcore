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
import spray.routing.{ HttpService, RequestContext }
import spray.routing.directives.CachingDirectives
import spray.json.DefaultJsonProtocol
import spray.can.Http
import spray.httpx.marshalling.Marshaller
import spray.httpx.encoding.Gzip
import spray.util._
import spray.http._
import MediaTypes._
import CachingDirectives._
import reflect.ClassTag

import com.coinport.coinex.LocalRouters
import com.coinport.coinex.data._

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val actorStatsFormat = jsonFormat1(ActorStats)
}

import MyJsonProtocol._

class Monitor(val routers: LocalRouters) extends Actor with HttpService with spray.httpx.SprayJsonSupport {
  val actorRefFactory = context
  implicit def executionContext = context.dispatcher
  implicit val timeout: Timeout = 1 second
  def receive = runRoute(route)

  val route = {
    get {
      pathSingleSlash {
        complete {
          context.actorSelection("/user/market_processor_btc_rmb/singleton")
            .ask(QueryActorStats)
            .mapTo[ActorStats]
        }
      } ~
        path("config") {
          complete("Pong")
        }
    }
  }
}