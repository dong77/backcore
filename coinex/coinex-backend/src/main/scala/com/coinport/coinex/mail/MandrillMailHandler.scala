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

// TODO(d): use a seperate dispatcher?
class MandrillMailHandler(implicit val system: ActorSystem) extends MailHandler {

  val apiKey = "YqW5g_wxhP0rSwV59-QbOQ"
  val endpoint = "https://mandrillapp.com/api/1.0/"
  val registerationEmailConfirmTemplate = "registerationemailconfirm"

  case class TemplateContent(name: String, content: String)
  case class To(email: String, `type`: String = "to")
  case class Message(subject: String, to: Seq[To], from_email: String = "noreply@coinport.com", important: Boolean = true)
  case class SendTemplateRequest(template_name: String, template_content: Seq[TemplateContent] = Nil, message: Message,
    key: String = apiKey, async: Boolean = true, ip_pool: String = "Main Pool")

  case class SendTemplateResponse(email: String, status: String, reject_reason: String, _id: String)

  object ElevationJsonProtocol extends DefaultJsonProtocol {
    implicit val templateContentFormat = jsonFormat2(TemplateContent)
    implicit val toFormat = jsonFormat2(To)
    implicit val messageFormat = jsonFormat4(Message)
    implicit val sendTemplateRequestFormat = jsonFormat6(SendTemplateRequest)
    implicit val sendTemplateResponseFormat = jsonFormat4(SendTemplateResponse)
  }

  private def generateSendTemplateRequest(template: String, subject: String, to: String) =
    SendTemplateRequest(template_name = template, message = Message(subject = subject, to = Seq(To(to))))

  import system.dispatcher
  import SprayJsonSupport._
  import ElevationJsonProtocol._

  val pipeline: HttpRequest => Future[Seq[SendTemplateResponse]] = (
    encode(Gzip)
    ~> sendReceive
    ~> decode(Deflate)
    ~> unmarshal[Seq[SendTemplateResponse]])

  def sendRegistrationEmailConfirmation(to: String, params: Seq[(String, String)]) = {
    val req = SendTemplateRequest(
      template_name = registerationEmailConfirmTemplate,
      template_content = params.map { case (k, v) => TemplateContent(k, v) },
      message = Message(subject = "Welcome to Coinport", to = Seq(To(to))))

    pipeline {
      Post(endpoint + "messages/send-template.json", req)
    } onComplete {
      case Success(x) => println("=" * 30 + x)
      case Failure(error) => println(error, "=" * 100 + "Couldn't get elevation")
    }
  }
}