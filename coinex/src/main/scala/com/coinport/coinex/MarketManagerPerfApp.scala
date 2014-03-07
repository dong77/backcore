package com.coinport.coinex.domain

// Test if MarketManager can handle millions of orders
object MarketManagerPerfApp extends App {
  val takerSide = BTC ~> RMB
  val makerSide = takerSide.reverse

  val mm = new MarketManager(takerSide)
  mm.disableCollectingTransactions()

  val roof = 1000 * 1000
  val s = System.currentTimeMillis()
  (1 to roof) foreach { i => mm.addOrder(Order(makerSide, OrderData(id = i, price = 1.0 / BigDecimal(i), quantity = i))) }
  val s2 = System.currentTimeMillis()
  println("submit " + roof + " orders took " + (s2 - s) + " ms")

  mm.addOrder(Order(takerSide, OrderData(id = roof + 1, price = 1, quantity = roof + 1))).size

  println("maching orders took " + (System.currentTimeMillis() - s2) + " ms")
  println(mm().orderMap)
}