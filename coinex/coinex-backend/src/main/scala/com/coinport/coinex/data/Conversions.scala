/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 * All classes here are case-classes or case-objects. This is required since we are
 * maintaining an in-memory state that's immutable, so that while snapshot is taken,
 * the in-memory state can still be updated.
 */

package com.coinport.coinex.data

import scala.collection.immutable.Map

class RichPersistentAccountState(raw: PersistentAccountState) {
  def toPojo = new AccountState(raw.lastOrderId, raw.userAccountsMap.toMap)
}

class RichAccountState(raw: AccountState) {
  def toThrift = PersistentAccountState(raw.lastOrderId, raw.userAccountsMap)
}

object Conversions {
  implicit def accountState2Rich(raw: AccountState) = new RichAccountState(raw)
  implicit def persistentAccountState2Rich(raw: PersistentAccountState) = new RichPersistentAccountState(raw)
}