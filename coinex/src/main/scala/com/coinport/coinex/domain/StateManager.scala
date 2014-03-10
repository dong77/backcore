/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 *
 * MarketManager is the maintainer of a Market. It executes new orders before
 * they are added into a market as pending orders. As execution results, a list
 * of Transactions are generated and returned.
 *
 * MarketManager can be used by an Akka persistent processor or a view
 * to reflect pending orders and market depth.
 *
 * Note this class does NOT depend on event-sourcing framework we choose. Please
 * keep it plain old scala/java.
 */

package com.coinport.coinex.domain

trait StateManager[T] {
  protected var defaultState: T = null.asInstanceOf[T]
  protected var state: T = null.asInstanceOf[T]

  def initWithDefaultState(defaultState: T) = {
    this.defaultState = defaultState
    state = defaultState
  }

  def apply() = state

  def reset(state: T) {
    this.state = Option(state).getOrElse(defaultState)
  }
}
