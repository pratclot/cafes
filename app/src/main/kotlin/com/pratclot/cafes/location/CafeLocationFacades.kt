package com.pratclot.cafes.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class UserPosition(
  val latitude: Double,
  val longitude: Double,
  val accuracyMeters: Float,
  val observedAtMillis: Long,
) {
  fun distanceTo(latitude: Double, longitude: Double): Float {
    val result = FloatArray(1)
    Location.distanceBetween(this.latitude, this.longitude, latitude, longitude, result)
    return result[0]
  }
}

data class CafeCandidate(
  val id: String,
  val name: String,
  val address: String?,
  val latitude: Double?,
  val longitude: Double?,
) {
  fun distanceFrom(position: UserPosition): Float? = if (latitude == null || longitude == null) null else position.distanceTo(latitude, longitude)
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
  companion object {
    fun create(context: Context): CafeLocationFacades = CafeLocationFacades(
      location = PlayServicesLocationFacade(context),
      address = AndroidAddressLookupFacade(context),
      cafes = OpenStreetMapCafeFacade(),
    )
  }
}

private class PlayServicesLocationFacade(context: Context) : LocationFacade {
  private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

  @Suppress("MissingPermission")
  override suspend fun refresh(): UserPosition? {
    val cancellation = CancellationTokenSource()
    return try {
      val request = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
        .setMaxUpdateAgeMillis(0L)
        .setDurationMillis(20_000L)
        .build()
      client.getCurrentLocation(request, cancellation.token)
        .await(cancellation)
        ?.takeIf { it.isRecent() }
        ?.toUserPosition()
    } finally {
      cancellation.cancel()
    }
  }

  @Suppress("MissingPermission")
  override fun observeWhileInUse(): Flow<UserPosition> = callbackFlow {
    val callback = object : LocationCallback() {
      override fun onLocationResult(result: LocationResult) {
        result.lastLocation?.takeIf { it.isRecent() }?.let { trySend(it.toUserPosition()) }
      }
    }

    // A fresh location will follow; a recent cached fix avoids an empty indicator on startup.
    client.lastLocation.addOnSuccessListener { location ->
      if (location != null && location.isRecent()) {
        trySend(location.toUserPosition())
      }
    }

    val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
      .setMinUpdateDistanceMeters(20f)
      .setWaitForAccurateLocation(false)
      .build()
    client.requestLocationUpdates(request, callback, Looper.getMainLooper())
      .addOnFailureListener { close(it) }

    awaitClose { client.removeLocationUpdates(callback) }
  }

  private fun Location.toUserPosition() = UserPosition(latitude, longitude, accuracy, time)

  private fun Location.isRecent(): Boolean {
    val ageMillis = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L
    return ageMillis in 0L..60_000L
  }

  private suspend fun <T> Task<T>.await(cancellation: CancellationTokenSource): T =
    suspendCancellableCoroutine { continuation ->
      continuation.invokeOnCancellation { cancellation.cancel() }
      addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
      addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
      addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
    }
}

private class AndroidAddressLookupFacade(private val context: Context) : AddressLookupFacade {
  @Suppress("DEPRECATION")
  override suspend fun describe(position: UserPosition): String? = withContext(Dispatchers.IO) {
    runCatching {
      if (!Geocoder.isPresent()) return@withContext null
      val result = Geocoder(context, Locale.getDefault())
        .getFromLocation(position.latitude, position.longitude, 1)
        ?.firstOrNull() ?: return@withContext null
      listOfNotNull(result.subLocality, result.thoroughfare, result.locality)
        .distinct()
        .joinToString(", ")
        .ifBlank { null }
    }.getOrNull()
  }
}

/**
 * Prototype provider using OpenStreetMap's public Overpass endpoint. Inject a different endpoint
 * or replace this facade with a hosted/commercial places provider without changing the UI.
 */
private class OpenStreetMapCafeFacade(
  private val europeanEndpoint: String = "https://ovp.dresel.systems/api/interpreter",
  private val globalEndpoint: String = "https://overpass-api.de/api/interpreter",
) : NearbyCafeFacade {
  override suspend fun findNearby(position: UserPosition): List<CafeCandidate> = withContext(Dispatchers.IO) {
    val query = """
      [out:json][timeout:12];
      (
        nwr(around:350,${position.latitude},${position.longitude})["amenity"="cafe"];
        nwr(around:350,${position.latitude},${position.longitude})["shop"="coffee"];
      );
      out center tags;
    """.trimIndent()
    val endpoints = if (position.latitude in 34.0..72.0 && position.longitude in -25.0..45.0) {
      listOf(europeanEndpoint, globalEndpoint)
    } else {
      listOf(globalEndpoint)
    }
    var lastFailure: Exception? = null
    for (endpoint in endpoints) {
      try {
        return@withContext queryEndpoint(endpoint, query, position)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        lastFailure = failure
      }
    }
    throw lastFailure ?: IOException("No Overpass endpoint is configured")
  }

  private fun queryEndpoint(endpoint: String, query: String, position: UserPosition): List<CafeCandidate> {
    val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = 8_000
      readTimeout = 15_000
      doOutput = true
      setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
      setRequestProperty("User-Agent", "PratclotCafes/1.0 (Android cafe log)")
    }
    return try {
      val body = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
      connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
      if (connection.responseCode !in 200..299) {
        throw IOException("Overpass returned HTTP ${connection.responseCode}")
      }
      val response = connection.inputStream.bufferedReader().use { it.readText() }
      val elements = JSONObject(response).optJSONArray("elements") ?: return emptyList()
      (0 until elements.length()).mapNotNull { index ->
        val element = elements.optJSONObject(index) ?: return@mapNotNull null
        val tags = element.optJSONObject("tags") ?: return@mapNotNull null
        val name = tags.optString("name").takeIf { it.isNotBlank() }
          ?: tags.optString("brand").takeIf { it.isNotBlank() }
          ?: "Cafe"
        val center = element.optJSONObject("center")
        val latitude = if (center != null) center.optDouble("lat", Double.NaN) else element.optDouble("lat", Double.NaN)
        val longitude = if (center != null) center.optDouble("lon", Double.NaN) else element.optDouble("lon", Double.NaN)
        if (!latitude.isFinite() || !longitude.isFinite()) return@mapNotNull null
        val street = tags.optString("addr:street").takeIf { it.isNotBlank() }
        val number = tags.optString("addr:housenumber").takeIf { it.isNotBlank() }
        val address = listOfNotNull(listOfNotNull(street, number).joinToString(" ").ifBlank { null }, tags.optString("addr:city").takeIf { it.isNotBlank() })
          .joinToString(", ").ifBlank { null }
        CafeCandidate(
          id = "${element.optString("type")}:${element.optLong("id")}",
          name = name,
          address = address,
          latitude = latitude,
          longitude = longitude,
        )
      }.distinctBy { it.id }.sortedBy { it.distanceFrom(position) ?: Float.MAX_VALUE }
    } finally {
      connection.disconnect()
    }
  }
}

fun hasLocationPermission(context: Context): Boolean =
  ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
