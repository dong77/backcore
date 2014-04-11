package com.coinport.coinex.common

import com.coinport.coinex.data.RedeliverFilterData

abstract class AbstractManager[T <: AnyRef] {
  protected var filters: Map[String, RedeliverFilter] = Map.empty

  def getSnapshot: T
  def loadSnapshot(s: T): Unit

  def seen(channel: String, id: Long) = {
    if (!filters.contains(channel)) {
      filters += (channel -> new RedeliverFilter(RedeliverFilterData(Seq.empty[Long], 10), 10))
    }
    filters(channel).seen(id)
  }
}
