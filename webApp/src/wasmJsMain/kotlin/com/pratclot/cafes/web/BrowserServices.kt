package com.pratclot.cafes.web

import com.pratclot.cafes.core.model.AddressLookupFacade
import com.pratclot.cafes.core.model.CafeCandidate
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.CafeLocationFacades
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import com.pratclot.cafes.core.model.LocationFacade
import com.pratclot.cafes.core.model.NearbyCafeFacade
import com.pratclot.cafes.core.model.UserPosition
import com.pratclot.cafes.data.CafeEntryListOps
import com.pratclot.cafes.data.CafeLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val ACTIVE_KEY = "cafe-log-active"
private const val ARCHIVED_KEY = "cafe-log-archived"

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

internal class BrowserCafeLogRepository(
  private val onStorageWarning: (String?) -> Unit,
) : CafeLogRepository {
  override fun load(): List<CafeEntry> = readEntries(ACTIVE_KEY)

  override fun loadArchived(): List<CafeEntry> = readEntries(ARCHIVED_KEY)

  override fun add(entry: CafeEntry): List<CafeEntry> {
    val active = CafeEntryListOps.add(load(), entry)
    persist(active, loadArchived())
    return active
  }

  override fun archive(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.archive(load(), loadArchived(), entry)
    persist(result.first, result.second)
    return result
  }

  override fun restore(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.restore(load(), loadArchived(), entry)
    persist(result.first, result.second)
    return result
  }

  override fun appendPhotos(entryId: String, newPhotoPaths: List<String>): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.appendPhotos(load(), loadArchived(), entryId, newPhotoPaths)
    persist(result.first, result.second)
    return result
  }

  override fun update(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> {
    val result = CafeEntryListOps.update(load(), loadArchived(), entry)
    persist(result.first, result.second)
    return result
  }

  private fun persist(active: List<CafeEntry>, archived: List<CafeEntry>) {
    val activeSaved = browserStorageWrite(ACTIVE_KEY, writeEntries(active))
    val archivedSaved = browserStorageWrite(ARCHIVED_KEY, writeEntries(archived))
    onStorageWarning(
      if (activeSaved && archivedSaved) {
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

private fun readEntries(key: String): List<CafeEntry> =
  browserStorageRead(key)?.let(::parseEntries).orEmpty()

private fun parseEntries(data: String): List<CafeEntry> = data.lineSequence().mapNotNull { line ->
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

private fun writeEntries(entries: List<CafeEntry>): String = entries.joinToString("\n") { entry ->
  listOf(
    entry.id, entry.photoPath, entry.venue, entry.address, entry.visitedAt.toString(),
    entry.latitude?.toString().orEmpty(), entry.longitude?.toString().orEmpty(), entry.photoPaths.joinToString(","),
    entry.manualTags.joinToString(","), entry.autoTags.joinToString(","), entry.taggingModelVersion.orEmpty(),
    serializeClassificationRuns(entry.classificationRuns),
  ).joinToString("|") { encodeBrowserValue(it) }
}

private fun serializeClassificationRuns(runs: List<ClassificationRun>): String = runs.joinToString("\u001e") { run ->
  listOf(
    run.occurredAt.toString(), run.modelVersion, run.photoCount.toString(), run.durationMillis.toString(), run.outcome,
    run.labels.joinToString("\u001d") { "${encodeBrowserValue(it.name)}\u001c${it.confidence}" },
    run.errorMessage.orEmpty(),
  ).joinToString("\u001f") { encodeBrowserValue(it) }
}

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
