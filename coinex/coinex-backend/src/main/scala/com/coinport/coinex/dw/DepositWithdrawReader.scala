package com.coinport.coinex.dw

import akka.actor.Actor
import com.mongodb.casbah.MongoDB

// TODO(xi): implement this class and deploy it using router.
class DepositWithdrawView(val db: MongoDB) extends Actor with DepositWithdrawBehavior {
  def receive = {
    case _ =>
  }
}