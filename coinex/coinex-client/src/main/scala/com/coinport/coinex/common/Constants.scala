/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.common

// TODO(c): make these case objects
object Constants {
  val COINPORT_UID = 3142141421L
  val TRANSACTION = "translaction"
  val WITHDRAWAL = "withdrawal"
  val STOP_ORDER_ROBOT_TYPE = 1
  val TRAILING_STOP_ORDER_ROBOT_TYPE = 2

  val ALL_ROLES = """
    user_processor,
    user_view,
    user_mpview,
    
    account_processor,
    account_view,
    
    api_auth_processor,
    api_auth_view,
    
    marke_update_processor,
    
    market_processor_btc_rmb,
    market_depth_view_btc_rmb,
    candle_data_view_btc_rmb,
    transaction_data_view_btc_rmb,
    user_transaction_view_btc_rmb,


    user_orders_view,
    metrics_view,
    mailer,


 
    robot_processor,
    dw_processor,
    
    user_processor_event_export,
    account_processor_event_export,
    market_processor_event_export_btc_rmb,
    """

  type MarketEvent = (Option[Double], Option[Long]) // (price, volume)

  val _24_HOURS: Long = 3600 * 24 * 1000
  val _10_SECONDS: Long = 10 * 1000

  def ascending = (lhs: Double, rhs: Double) => lhs <= rhs
  def descending = (lhs: Double, rhs: Double) => lhs >= rhs
}
