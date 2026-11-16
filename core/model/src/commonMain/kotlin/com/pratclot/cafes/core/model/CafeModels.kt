package com.pratclot.cafes.core.model

import kotlinx.coroutines.flow.Flow

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
  val manualTags: List<String> = emptyList(),
  val autoTags: List<String> = emptyList(),
  val taggingModelVersion: String? = null,
  val classificationRuns: List<ClassificationRun> = emptyList(),
)

data class FoodLabel(val name: String, val confidence: Float)

data class ClassificationRun(
  val occurredAt: Long,
  val modelVersion: String,
  val photoCount: Int,
  val durationMillis: Long,
  /** One of SUCCESS, NO_DETECTIONS, or ERROR. */
  val outcome: String,
  val labels: List<FoodLabel> = emptyList(),
  val errorMessage: String? = null,
)

/** Platform boundary for local food-photo classification. */
interface FoodTaggerFacade {
  suspend fun classify(photoPaths: List<String>): List<FoodLabel>
}

data class UserPosition(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val observedAtMillis: Long,
) {
  fun distanceTo(latitude: Double, longitude: Double): Float {
    val earthRadiusMeters = 6_371_000.0
    val latitudeDelta = (latitude - this.latitude) * kotlin.math.PI / 180.0
    val longitudeDelta = (longitude - this.longitude) * kotlin.math.PI / 180.0
    val startLatitude = this.latitude * kotlin.math.PI / 180.0
    val endLatitude = latitude * kotlin.math.PI / 180.0
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

/** Facade for foreground-only location updates. */
interface LocationFacade {
  fun observeWhileInUse(): Flow<UserPosition>
  suspend fun refresh(): UserPosition?
}

/** Facade for turning coordinates into a readable street/locality hint. */
interface AddressLookupFacade {
  suspend fun describe(position: UserPosition): String?
}

/** Facade for finding cafe candidates near a coordinate. */
interface NearbyCafeFacade {
  suspend fun findNearby(position: UserPosition): List<CafeCandidate>
}

data class CafeLocationFacades(
  val location: LocationFacade,
  val address: AddressLookupFacade,
  val cafes: NearbyCafeFacade,
) {
  companion object
}
