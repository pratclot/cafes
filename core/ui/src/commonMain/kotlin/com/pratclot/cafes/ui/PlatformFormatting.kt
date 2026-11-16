package com.pratclot.cafes.ui

import kotlin.math.roundToLong

expect fun formatTimestamp(epochMillis: Long): String

fun formatCoordinate(value: Double, decimals: Int = 5): String {
  var factor = 1.0
  repeat(decimals) { factor *= 10.0 }
  val rounded = (value * factor).roundToLong()
  val negative = rounded < 0
  val absVal = if (negative) -rounded else rounded
  val factorLong = factor.toLong()
  val intPart = absVal / factorLong
  val fracPart = (absVal % factorLong).toString().padStart(decimals, '0')
  return "${if (negative) "-" else ""}$intPart.$fracPart"
}
