/**
 * Copyright (C) 2014 Coinport Inc.
 */
package com.coinport.coinex.api.model

import org.specs2.mutable._
import org.json4s.JsonDSL._

class JsonTest extends Specification {
  "models to JSON" should {
    "ApiResult to JSON" in {
      val result = ApiResult(true, 0, "some message")
      val json =
        ("success" -> true) ~
          ("code" -> 0) ~
          ("message" -> "some message")

      result.toJson mustEqual json
    }
  }
}