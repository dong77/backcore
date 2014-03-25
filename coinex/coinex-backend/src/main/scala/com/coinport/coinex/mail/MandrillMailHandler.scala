/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex.mail

import com.coinport.coinex.data._

import scala.util.{ Success, Failure }
import scala.concurrent.Future

import akka.actor._
import akka.pattern.ask
import akka.event.Logging

import spray.http._
import spray.httpx.encoding._
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import spray.util._
import spray.json._

import org.slf4s.Logging

class MandrillMailHandler(mandrillApiKey: String)(implicit val system: ActorSystem) extends MailHandler with Logging {

  val url = "https://mandrillapp.com/api/1.0/messages/send-template.json"

  val registerConfimTemplate = "registerconfirm"
  val loginTokenTemplate = "logintoken"
  val passwordResetTemplate = "passwordreset"

  case class TemplateContent(name: String, content: String)
  case class To(email: String)
  case class Message(to: Seq[To], important: Boolean = true)
  case class SendTemplateRequest(template_name: String, template_content: Seq[TemplateContent], message: Message, async: Boolean = false, key: String = mandrillApiKey)
  case class SendTemplateResponse(email: String, status: String, reject_reason: String, _id: String)

  object ElevationJsonProtocol extends DefaultJsonProtocol {
    implicit val templateContentFormat = jsonFormat2(TemplateContent)
    implicit val toFormat = jsonFormat1(To)
    implicit val messageFormat = jsonFormat2(Message)
    implicit val sendTemplateRequestFormat = jsonFormat5(SendTemplateRequest)
    implicit val sendTemplateResponseFormat = jsonFormat4(SendTemplateResponse)
  }

  import system.dispatcher
  import SprayJsonSupport._
  import ElevationJsonProtocol._

  val pipeline: HttpRequest => Future[HttpResponse] = (addHeader("Content-Type", "application/json; charset=utf-8") ~> sendReceive)

  def sendRegistrationEmailConfirmation(to: String, params: Seq[(String, String)]) = sendMail(to, registerConfimTemplate, params)

  def sendLoginToken(to: String, params: Seq[(String, String)]) = sendMail(to, loginTokenTemplate, params)

  def sendPasswordReset(to: String, params: Seq[(String, String)]) = sendMail(to, passwordResetTemplate, params)

  private def sendMail(to: String, template: String, params: Seq[(String, String)]) = {
    val tc = params.map { case (k, v) => TemplateContent(k, v) }
    val req = SendTemplateRequest(template, tc, Message(to = Seq(To(to))))

    def trySendMail(maxTry: Int): Unit = {
      if (maxTry > 0) pipeline { Post(url, req) } onComplete {
        case Success(response) if response.status == StatusCodes.OK =>
          log.debug("email send for request: " + req.toJson.prettyPrint + ". response is: " + response)

        case Success(response) =>
          log.error("send-mail failed for request " + req.toJson.prettyPrint + ". response is: " + response)
          trySendMail(maxTry - 1)

        case Failure(error) =>
          log.error("send-mail failed for request " + req.toJson.prettyPrint + ". error is: " + error)
          trySendMail(maxTry - 1)
      }
    }
    trySendMail(2)
  }
}