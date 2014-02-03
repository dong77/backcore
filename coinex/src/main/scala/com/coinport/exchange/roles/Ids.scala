package com.coinport.exchange.roles

object Ids {
  def BALANCE_PROCESSOR = "balance_processor"
  def DEPOSIT_WITHDRAWAL_PROCESSOR = "deposit_withdrawal_processor"
  def MARKET_PROCESSOR(market: String) = "market_%s_processor".format(market)
  def DEPOSIT_WITHDRAWAL_TO_BALANCE_CHANNEL = "deposit_withdrawal_2_balance_channel"
  def BALANCE_TO_MARKET_CHANNEL(market: String) = "balance_2_market_%s_channel".format(market)
}