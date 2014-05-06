package com.coinport.coinex.api.model

object NotificationType extends Enumeration {
  val Success, Info, Warning, Danger = Value
}

case class Notification(`type`: NotificationType.Value, message: String)
