package com.pratclot.cafes.core.model

/** A saved cafe visit. The first photo path remains as a compatibility field for older records. */
data class CafeEntry(
  val id: String,
  val photoPath: String,
  val venue: String,
  val address: String,
  val visitedAt: Long,
  val latitude: Double?,
  val longitude: Double?,
  val photoPaths: List<String> = listOf(photoPath),
)

data class UserPosition(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val observedAtMillis: Long,
) {
  fun distanceTo(latitude: Double, longitude: Double): Float {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = Math.toRadians(latitude - this.latitude)
    val longitudeDelta = Math.toRadians(longitude - this.longitude)
    val startLatitude = Math.toRadians(this.latitude)
    val endLatitude = Math.toRadians(latitude)
    val haversine = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
      kotlin.math.cos(startLatitude) * kotlin.math.cos(endLatitude) *
      kotlin.math.sin(longitudeDelta / 2).let { it * it }
    return (2 * earthRadiusMeters * kotlin.math.asin(kotlin.math.sqrt(haversine.coerceIn(0.0, 1.0)))).toFloat()
  }
}

data class CafeCandidate(
  val id: String,
  val name: String,
  val address: String?,
  val latitude: Double?,
  val longitude: Double?,
) {
  fun distanceFrom(position: UserPosition): Float? =
    if (latitude == null || longitude == null) null else position.distanceTo(latitude, longitude)
}
