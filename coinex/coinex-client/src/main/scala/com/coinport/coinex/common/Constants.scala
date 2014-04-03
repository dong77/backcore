/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.common

object Constants {
  val COINPORT_UID = 3142141421L
  val TRANSACTION = "translaction"
  val WITHDRAWAL = "withdrawal"
  val STOP_ORDER_ROBOT_TYPE = 1
  val TRAILING_STOP_ORDER_ROBOT_TYPE = 2

  val ALL_ROLES = """
    user_processor,
    account_processor,
    marke_update_processor,
    market_processor_btc_rmb,
    market_depth_view_btc_rmb,
    user_view,
    account_view,
    user_orders_view,
    candle_data_view_btc_rmb,
    mailer,
    metrics_view,
    transaction_data_view_btc_rmb,
    user_transaction_view_btc_rmb,
    api_auth_processor,
    api_auth_view,
    robot_processor,
    user_processor_mpv,
    account_processor_mpv,
    market_processor_mpv_btc_rmb,
    dw_processor,
    """
}
