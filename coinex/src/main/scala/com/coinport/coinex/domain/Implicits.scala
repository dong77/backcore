package com.coinport.coinex.domain

object Implicits {
  class RichCurrency(c: Currency) {
    def ~>(another: Currency) = MarketSide(c, another)
    def <~(another: Currency) = MarketSide(another, c)
  }

  class RichMarketSide(side: MarketSide) {
    def reverse = MarketSide(side.inCurrency, side.outCurrency)
  }

  class RichTransaction(tx: Transaction) {
    def takerPrice = tx.maker.quantity / tx.taker.quantity
    def makerPrice = tx.taker.quantity / tx.maker.quantity
  }

  implicit def currency2Rich(c: Currency) = new RichCurrency(c)
  implicit def marketSide2Rich(side: MarketSide) = new RichMarketSide(side)
  implicit def transaction2Rich(tx: Transaction) = new RichTransaction(tx)
}