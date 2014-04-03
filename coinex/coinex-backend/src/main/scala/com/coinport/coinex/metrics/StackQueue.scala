/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.coinex.metrics

import scala.collection.mutable.ArrayBuffer

class StackQueue[T](elems: ArrayBuffer[T], var head: Int,
    ordering: (T, T) => Boolean)(implicit m: Manifest[T]) extends Serializable {

  def this(ordering: (T, T) => Boolean)(implicit m: Manifest[T]) = this(new ArrayBuffer[T](), 0, ordering)

  def push(elem: T): StackQueue[T] = {
    val lastIndex = lastIndexWhere((e: T) => ordering(e, elem)) + 1
    if (lastIndex == 0) {
      elems.clear()
      head = 0
    }
    elems.remove(lastIndex, elems.length - lastIndex)
    elems += elem
    this
  }

  def dequeue(elem: T): StackQueue[T] = {
    front match {
      case Some(f) if (f == elem) =>
        head += 1
        if (elems.length <= head) {
          elems.clear()
          head = 0
        }
      case _ => None
    }
    this
  }

  def front: Option[T] = if (elems.length == 0) None else Some(elems(head))

  def copy = new StackQueue[T](elems.slice(head, elems.length), 0, ordering)

  def toList = elems.slice(head, elems.length).toList

  override def toString() = "StackQueue%s".format(toList).replace("List", "")

  private def lastIndexWhere(predict: (T) => Boolean): Int = {
    for (i <- elems.length - 1 to 0 by -1)
      if (predict(elems(i)))
        return i
    return -1
  }
}
