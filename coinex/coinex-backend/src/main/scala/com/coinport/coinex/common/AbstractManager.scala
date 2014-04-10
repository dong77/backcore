package com.coinport.coinex.common

import com.twitter.scrooge.ThriftStruct

import com.coinport.coinex.data.RedeliverFilterData

abstract class AbstractManager[T <: ThriftStruct] {
  protected var filters: Map[String, RedeliverFilter] = Map.empty

  def getSnapshot: T
  def loadSnapshot(s: T): Unit

  def initFilters(channelsName: List[String]) {
    channelsName foreach { channel =>
      filters += (channel -> new RedeliverFilter(RedeliverFilterData(Seq.empty[Long], 10), 10))
    }
  }

  def seen(channel: String, id: Long) = filters(channel).seen(id)
}
