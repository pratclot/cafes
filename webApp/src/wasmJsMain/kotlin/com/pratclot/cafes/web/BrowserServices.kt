package com.pratclot.cafes.web

import com.pratclot.cafes.core.model.CafeCandidate

/** Browser-side seams so geolocation and venue providers can be replaced independently. */
internal interface BrowserLocationFacade {
  fun currentPosition(onResult: (latitude: Double, longitude: Double, accuracyMeters: Double, error: String) -> Unit)
}

internal interface BrowserNearbyCafeFacade {
  fun nearby(latitude: Double, longitude: Double, onResult: (List<CafeCandidate>, failed: Boolean) -> Unit)
}

internal class BrowserGeolocationFacade : BrowserLocationFacade {
  override fun currentPosition(
    onResult: (latitude: Double, longitude: Double, accuracyMeters: Double, error: String) -> Unit,
  ) = browserRequestLocation(onResult)
}

internal class OpenStreetMapBrowserCafeFacade : BrowserNearbyCafeFacade {
  override fun nearby(
    latitude: Double,
    longitude: Double,
    onResult: (List<CafeCandidate>, failed: Boolean) -> Unit,
  ) {
    browserFindNearbyCafes(latitude, longitude) { rows ->
      onResult(parseCandidates(rows), rows == "!ERROR")
    }
  }
}

private fun parseCandidates(data: String): List<CafeCandidate> {
  if (data == "!ERROR") return emptyList()
  return data.lineSequence().mapNotNull { row ->
    val fields = row.split('|')
    if (fields.size != 5) return@mapNotNull null
    runCatching {
      CafeCandidate(
        id = decodeBrowserValue(fields[0]),
        name = decodeBrowserValue(fields[1]),
        address = decodeBrowserValue(fields[2]).ifBlank { null },
        latitude = decodeBrowserValue(fields[3]).toDoubleOrNull(),
        longitude = decodeBrowserValue(fields[4]).toDoubleOrNull(),
      )
    }.getOrNull()
  }.toList()
}
