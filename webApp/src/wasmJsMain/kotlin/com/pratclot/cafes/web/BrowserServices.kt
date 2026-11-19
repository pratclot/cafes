package com.pratclot.cafes.web

import com.pratclot.cafes.core.model.AddressLookupFacade
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.CafeLocationFacades
import com.pratclot.cafes.core.model.CafeLog
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.LocationFacade
import com.pratclot.cafes.core.model.NearbyCafeFacade
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.data.CafeEntryListOps
import com.pratclot.cafes.data.CafeLogRepository
import com.pratclot.cafes.data.CafeLogSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

private const val LEGACY_ACTIVE_KEY = "cafe-log-active"
private const val LEGACY_ARCHIVED_KEY = "cafe-log-archived"
private const val SNAPSHOT_KEY = "cafe-log-snapshot-v2"

internal fun createBrowserLocationFacades(): CafeLocationFacades = CafeLocationFacades(
  location = BrowserGeolocationFacade(),
  address = BrowserAddressLookupFacade(),
  cafes = OpenStreetMapBrowserCafeFacade(),
)

internal class BrowserGeolocationFacade : LocationFacade {
  override fun observeWhileInUse(): Flow<UserPosition> = emptyFlow()

  override suspend fun refresh(): UserPosition? = suspendCancellableCoroutine { continuation ->
    var cancelled = false
    continuation.invokeOnCancellation { cancelled = true }
    browserRequestLocation { latitude, longitude, accuracy, error ->
      if (cancelled || !continuation.isActive) return@browserRequestLocation
      if (error.isNotEmpty() || !latitude.isFinite() || !longitude.isFinite()) {
        continuation.resume(null)
      } else {
        continuation.resume(
          UserPosition(latitude, longitude, accuracy.toFloat(), browserNowMillis().toLong()),
        )
      }
    }
  }
}

internal class BrowserAddressLookupFacade : AddressLookupFacade {
  override suspend fun describe(position: UserPosition): String? = null
}

internal class OpenStreetMapBrowserCafeFacade : NearbyCafeFacade {
  override suspend fun findNearby(position: UserPosition): List<CafeCandidate> =
    suspendCancellableCoroutine { continuation ->
      var cancelled = false
      continuation.invokeOnCancellation { cancelled = true }
      browserFindNearbyCafes(position.latitude, position.longitude) { rows ->
        if (cancelled || !continuation.isActive) return@browserFindNearbyCafes
        if (rows == "!ERROR" || rows.startsWith("!ERROR|")) {
          val detail = rows.substringAfter('|', "").takeIf(String::isNotEmpty)?.let(::decodeBrowserValue)
          val message = detail ?: "Nearby cafe search failed"
          continuation.resumeWith(Result.failure(IllegalStateException(message)))
        } else {
          continuation.resume(parseCandidates(rows))
        }
      }
    }
}

internal suspend fun savePhotoBlobSuspend(key: String, dataUrl: String): Boolean =
  suspendCancellableCoroutine { continuation ->
    var cancelled = false
    continuation.invokeOnCancellation { cancelled = true }
    browserSavePhotoBlob(key, dataUrl) { ok ->
      if (!cancelled && continuation.isActive) continuation.resume(ok)
    }
  }

internal suspend fun loadPhotoBlobSuspend(key: String): String =
  suspendCancellableCoroutine { continuation ->
    var cancelled = false
    continuation.invokeOnCancellation { cancelled = true }
    browserLoadPhotoBlob(key) { dataUrl ->
      if (!cancelled && continuation.isActive) continuation.resume(dataUrl)
    }
  }

internal class BrowserCafeLogRepository(
  private val onStorageWarning: (String?) -> Unit,
) : CafeLogRepository {
  private val mutex = Mutex()
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  override suspend fun load(): List<CafeEntry> = mutex.withLock {
    readSnapshot().active
  }

  override suspend fun loadArchived(): List<CafeEntry> = mutex.withLock {
    readSnapshot().archived
  }

  override suspend fun add(entry: CafeEntry): List<CafeEntry> = mutex.withLock {
    val storedEntry = offloadEntryPhotos(entry)
    val snapshot = readSnapshot()
    val active = CafeEntryListOps.add(snapshot.active, storedEntry)
    persist(active, snapshot.archived)
    active
  }

  override suspend fun archive(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> = mutex.withLock {
    val snapshot = readSnapshot()
    val result = CafeEntryListOps.archive(snapshot.active, snapshot.archived, entry)
    persist(result.first, result.second)
    result
  }

  override suspend fun restore(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> = mutex.withLock {
    val snapshot = readSnapshot()
    val result = CafeEntryListOps.restore(snapshot.active, snapshot.archived, entry)
    persist(result.first, result.second)
    result
  }

  override suspend fun appendPhotos(entryId: String, newPhotoPaths: List<String>): Pair<List<CafeEntry>, List<CafeEntry>> =
    mutex.withLock {
      val storedPaths = newPhotoPaths.map { offloadPhoto(it) }
      val snapshot = readSnapshot()
      val result = CafeEntryListOps.appendPhotos(snapshot.active, snapshot.archived, entryId, storedPaths)
      persist(result.first, result.second)
      result
    }

  override suspend fun update(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> = mutex.withLock {
    val snapshot = readSnapshot()
    val result = CafeEntryListOps.update(snapshot.active, snapshot.archived, entry)
    persist(result.first, result.second)
    result
  }

  private suspend fun readSnapshot(): CafeLogSnapshot {
    val raw = browserStorageRead(SNAPSHOT_KEY)
    if (raw != null) {
      return runCatching { json.decodeFromString<CafeLogSnapshot>(raw) }
        .onFailure { error -> CafeLog.w("BrowserCafeLogRepository", "Failed to decode snapshot", error) }
        .getOrDefault(CafeLogSnapshot())
    }

    val legacyActive = browserStorageRead(LEGACY_ACTIVE_KEY)?.let(::parseLegacyEntries).orEmpty()
    val legacyArchived = browserStorageRead(LEGACY_ARCHIVED_KEY)?.let(::parseLegacyEntries).orEmpty()
    if (legacyActive.isEmpty() && legacyArchived.isEmpty()) {
      return CafeLogSnapshot()
    }
    val migrated = CafeLogSnapshot(
      active = legacyActive.map { offloadEntryPhotos(it) },
      archived = legacyArchived.map { offloadEntryPhotos(it) },
    )
    persist(migrated.active, migrated.archived)
    browserStorageRemove(LEGACY_ACTIVE_KEY)
    browserStorageRemove(LEGACY_ARCHIVED_KEY)
    return migrated
  }

  private suspend fun offloadEntryPhotos(entry: CafeEntry): CafeEntry {
    val storedPaths = entry.photoPaths.map { offloadPhoto(it) }
    val primary = storedPaths.firstOrNull() ?: offloadPhoto(entry.photoPath)
    return entry.copy(photoPath = primary, photoPaths = storedPaths)
  }

  private suspend fun offloadPhoto(path: String): String {
    if (!path.startsWith("data:")) return path
    val key = "idb://photo-${browserRandomUuid()}"
    return if (savePhotoBlobSuspend(key, path)) key else path
  }

  private fun persist(active: List<CafeEntry>, archived: List<CafeEntry>) {
    val saved = browserStorageWrite(SNAPSHOT_KEY, json.encodeToString(CafeLogSnapshot(active, archived)))
    onStorageWarning(
      if (saved) {
        null
      } else {
        "Browser storage is full or unavailable. This change may not survive a page reload."
      },
    )
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

private fun parseLegacyEntries(data: String): List<CafeEntry> = data.lineSequence().mapNotNull { line ->
  val fields = line.split('|')
  if (fields.size < 8) return@mapNotNull null
  runCatching {
    val photos = fields[7].takeIf(String::isNotEmpty)?.split(',').orEmpty().map(::decodeBrowserValue)
    val primary = decodeBrowserValue(fields[1])
    CafeEntry(
      id = decodeBrowserValue(fields[0]),
      photoPath = primary,
      venue = decodeBrowserValue(fields[2]),
      address = decodeBrowserValue(fields[3]),
      visitedAt = decodeBrowserValue(fields[4]).toLong(),
      latitude = decodeBrowserValue(fields[5]).toDoubleOrNull(),
      longitude = decodeBrowserValue(fields[6]).toDoubleOrNull(),
      photoPaths = photos.ifEmpty { listOf(primary) },
      manualTags = fields.getOrNull(8)?.let(::decodeBrowserValue)?.split(',')?.filter(String::isNotBlank).orEmpty(),
      autoTags = fields.getOrNull(9)?.let(::decodeBrowserValue)?.split(',')?.filter(String::isNotBlank).orEmpty(),
      taggingModelVersion = fields.getOrNull(10)?.let(::decodeBrowserValue)?.takeIf(String::isNotBlank),
      classificationRuns = parseClassificationRuns(fields.getOrNull(11)),
    )
  }.getOrNull()
}.toList()

private fun parseClassificationRuns(encoded: String?): List<ClassificationRun> {
  val data = encoded?.let(::decodeBrowserValue).orEmpty()
  if (data.isEmpty()) return emptyList()
  return data.split("\u001e").mapNotNull { rawRun ->
    runCatching {
      val fields = rawRun.split("\u001f").map(::decodeBrowserValue)
      val labels = fields.getOrNull(5).orEmpty().split("\u001d").filter(String::isNotBlank).mapNotNull { rawLabel ->
        val tagFields = rawLabel.split("\u001c")
        val name = tagFields.firstOrNull()?.let(::decodeBrowserValue).orEmpty()
        val confidence = tagFields.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        FoodLabel(name, confidence)
      }
      ClassificationRun(
        occurredAt = fields[0].toLong(),
        modelVersion = fields[1],
        photoCount = fields[2].toInt(),
        durationMillis = fields[3].toLong(),
        outcome = fields[4],
        labels = labels,
        errorMessage = fields.getOrNull(6)?.takeIf(String::isNotBlank),
      )
    }.getOrNull()
  }
}
