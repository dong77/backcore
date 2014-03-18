
/**
 * Copyright (C) 2014 Coinport Inc. <http://www.coinport.com>
 *
 */

package com.coinport.coinex

import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import ch.qos.logback.classic.spi.ILoggingEvent

// Filter out ClusterHeartbeat messages
class LogFilter extends Filter[ILoggingEvent] {
  def decide(event: ILoggingEvent) = {
    if (event.getLoggerName == "akka.cluster.ClusterHeartbeatSender") FilterReply.DENY
    else FilterReply.ACCEPT
  }
}