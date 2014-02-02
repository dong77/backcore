package com.coinport.exchange.roleplay

import akka.actor.Props

case class RoleSetting(props: Props, leaderProps: Option[Props] = None, registerAsService: Boolean = true) {
  def getLeaderProps = leaderProps.getOrElse(props)
}

class RolePlaySettings {
  private[roleplay] var propsMap = Map.empty[String, RoleSetting]

  def support(role: String, roleProps: RoleSetting) = {
    propsMap += role -> roleProps
    this
  }
}