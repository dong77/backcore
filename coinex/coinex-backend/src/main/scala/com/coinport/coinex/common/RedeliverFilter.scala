package com.coinport.coinex.common

import com.coinport.coinex.data.RedeliverFilterData
import scala.collection.mutable.SortedSet

class RedeliverFilter(state: RedeliverFilterData, maxSize: Int = -1) {
  private val max = if (maxSize <= 0) state.ids.size else maxSize
  assert(max > 0)
  private[common] var ids = SortedSet[Long](state.ids: _*).takeRight(max)

  def filter(id: Long)(op: Long => Unit) = if (!seen(id)) {
    op(id)
  }

  def getThrift = RedeliverFilterData(ids.toSeq, maxSize)

  def seen(id: Long) = {
    val isSeen = (id < ids.headOption.getOrElse(0L) || ids.contains(id))
    if (!isSeen) {
      ids += id
      if (ids.size > max) ids = ids.takeRight(max)
    }
    isSeen
  }
}
