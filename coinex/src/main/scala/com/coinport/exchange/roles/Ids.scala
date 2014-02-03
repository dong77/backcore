package com.coinport.exchange.roles

object Ids {
  def BALANCE_PROCESSOR = "balance_processor"
  def MARKET_PROCESSOR(market: String) = "market_%s_processor".format(market)
  def BALANCE_TO_MARKET_CHANNEL(market: String) = "balance_2_market_%s_channel".format(market)
}