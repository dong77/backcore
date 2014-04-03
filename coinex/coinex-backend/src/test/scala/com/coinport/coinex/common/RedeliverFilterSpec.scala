package com.coinport.coinex.common

import com.coinport.coinex.data.RedeliverFilterState
import org.specs2.mutable._

class RedeliverFilterSpec extends Specification {
  "RedeliverFilter" should {
    "restore state and restrict sizes" in {
      val state = RedeliverFilterState(Seq(10, 2, 4, 6, 19))
      val filter = new RedeliverFilter(state, 8)
      filter.ids.toSeq mustEqual Seq(2, 4, 6, 10, 19)

      var processed = 0
      filter.filter(0) { _ => failure("0 should be filtered out") }
      filter.filter(1) { _ => failure("1 should be filtered out") }
      filter.filter(2) { _ => failure("1 should be filtered out") }
      filter.filter(3) { _ => processed += 1 }
      filter.filter(4) { _ => failure("1 should be filtered out") }
      filter.filter(5) { _ => processed += 1 }
      filter.filter(6) { _ => failure("1 should be filtered out") }
      filter.filter(19) { _ => failure("1 should be filtered out") }
      filter.filter(20) { _ => processed += 1 }
      filter.filter(100) { _ => processed += 1 }
      filter.filter(101) { _ => processed += 1 }
      processed mustEqual 5
      filter.ids.toSeq mustEqual Seq(4, 5, 6, 10, 19, 20, 100, 101)
    }

    "keep state size if not specified" in {
      val state = RedeliverFilterState(Seq(10, 2, 4, 6, 19))
      val filter = new RedeliverFilter(state)

      filter.ids.toSeq mustEqual Seq(2, 4, 6, 10, 19)
      var processed = 0
      filter.filter(3) { _ => processed += 1 }
      processed mustEqual 1
      filter.ids.toSeq mustEqual Seq(3, 4, 6, 10, 19)
    }
  }
}
