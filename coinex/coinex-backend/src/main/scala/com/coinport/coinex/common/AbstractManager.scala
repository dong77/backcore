package com.coinport.coinex.common

import com.twitter.scrooge.ThriftStruct

abstract class AbstractManager[T <: ThriftStruct] {
  def getSnapshot: T
  def loadSnapshot(s: T): Unit
}
