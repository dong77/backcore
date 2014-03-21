package com.coinport.coinex.util

import java.security.MessageDigest

object Hash {
  def sha256(str: String) = MessageDigest.getInstance("SHA-256").digest(str.getBytes("UTF-8"));
  def murmur3(str: String): Long = MurmurHash3.MurmurHash3_x64_64(str.getBytes, 100416)
}