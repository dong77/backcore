/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * CoinditionalOrdersManager manages all conditional orders including
 *  - stop orders
 *  - trailing stop orders
 */

package com.coinport.coinex.domain

// TODO: implement this manager
class ConditionalOrdersManager(headSide: MarketSide) extends StateManager[ConditionalOrdersState] {
  initWithDefaultState(ConditionalOrdersState("someting"))

  def updateWithNewPrice(marketSide: MarketSide, price: Double): Seq[Order] = {
    // TODO
    // state = state.copy(...)
    Nil
  }
}