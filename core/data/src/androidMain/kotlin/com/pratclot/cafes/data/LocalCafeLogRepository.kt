package com.pratclot.cafes.data

import android.content.Context
import com.pratclot.cafes.core.model.CafeEntry
import com.pratclot.cafes.core.model.CafeLog
import com.pratclot.cafes.core.model.ClassificationRun
import com.pratclot.cafes.core.model.FoodLabel
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/** Local-first Android storage backed by atomic JSON file in filesDir, with legacy SharedPreferences migration. */
class LocalCafeLogRepository(context: Context) : CafeLogRepository {
  private val filesDir = context.filesDir
  private val storageFile = File(filesDir, "cafe_log.json")
  private val legacyPreferences = context.getSharedPreferences("cafe-log", Context.MODE_PRIVATE)
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
    val snapshot = readSnapshot()
    val active = CafeEntryListOps.add(snapshot.active, entry)
    writeSnapshot(snapshot.copy(active = active))
    active
  }

  override suspend fun archive(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> = mutex.withLock {
    val snapshot = readSnapshot()
    val (active, archived) = CafeEntryListOps.archive(snapshot.active, snapshot.archived, entry)
    writeSnapshot(CafeLogSnapshot(active = active, archived = archived))
    active to archived
  }

  override suspend fun restore(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> = mutex.withLock {
    val snapshot = readSnapshot()
    val (active, archived) = CafeEntryListOps.restore(snapshot.active, snapshot.archived, entry)
    writeSnapshot(CafeLogSnapshot(active = active, archived = archived))
    active to archived
  }

  override suspend fun appendPhotos(entryId: String, newPhotoPaths: List<String>): Pair<List<CafeEntry>, List<CafeEntry>> =
    mutex.withLock {
      val snapshot = readSnapshot()
      val (active, archived) = CafeEntryListOps.appendPhotos(snapshot.active, snapshot.archived, entryId, newPhotoPaths)
      writeSnapshot(CafeLogSnapshot(active = active, archived = archived))
      active to archived
    }

  override suspend fun update(entry: CafeEntry): Pair<List<CafeEntry>, List<CafeEntry>> = mutex.withLock {
    val snapshot = readSnapshot()
    val (active, archived) = CafeEntryListOps.update(snapshot.active, snapshot.archived, entry)
    writeSnapshot(CafeLogSnapshot(active = active, archived = archived))
    active to archived
  }

  private fun readSnapshot(): CafeLogSnapshot {
    if (!storageFile.exists()) {
      val migrated = migrateFromLegacyPreferences()
      if (migrated.active.isNotEmpty() || migrated.archived.isNotEmpty()) {
        writeSnapshot(migrated)
      }
      legacyPreferences.edit().clear().apply()
      return migrated
    }

    val raw = runCatching { storageFile.readText() }.getOrElse { error ->
      CafeLog.w("LocalCafeLogRepository", "Failed to read cafe_log.json", error)
      return CafeLogSnapshot()
    }
    val parsed = runCatching { json.decodeFromString<CafeLogSnapshot>(raw) }.getOrElse { error ->
      CafeLog.w("LocalCafeLogRepository", "Failed to decode cafe_log.json", error)
      return CafeLogSnapshot()
    }
    return CafeLogSnapshot(
      active = parsed.active.map(::sanitizePhotos).sortedByDescending { it.visitedAt },
      archived = parsed.archived.map(::sanitizePhotos).sortedByDescending { it.visitedAt },
    )
  }

  private fun sanitizePhotos(entry: CafeEntry): CafeEntry {
    val existing = entry.photoPaths.filter { path -> path.isNotBlank() && File(path).exists() }
    return if (existing.size == entry.photoPaths.size && entry.photoPath == existing.firstOrNull().orEmpty()) {
      entry
    } else {
      entry.copy(
        photoPath = existing.firstOrNull().orEmpty(),
        photoPaths = existing,
      )
    }
  }

  private fun writeSnapshot(snapshot: CafeLogSnapshot) {
    filesDir.mkdirs()
    val tmpFile = File(filesDir, "cafe_log.json.tmp")
    val encoded = json.encodeToString(snapshot)
    tmpFile.writeText(encoded)
    if (!tmpFile.renameTo(storageFile)) {
      storageFile.writeText(encoded)
      tmpFile.delete()
    }
    cleanupOrphanPhotos(snapshot)
  }

  private fun cleanupOrphanPhotos(snapshot: CafeLogSnapshot) {
    val referenced = (snapshot.active + snapshot.archived)
      .flatMap { it.photoPaths }
      .mapNotNull { path -> runCatching { File(path).canonicalPath }.getOrNull() }
      .toSet()
    filesDir.listFiles()?.forEach { file ->
      val name = file.name
      val isManagedPhoto = name.startsWith("cafe-") && (name.endsWith(".jpg") || name.endsWith(".photo"))
      if (file.isFile && isManagedPhoto) {
        val canonical = runCatching { file.canonicalPath }.getOrNull()
        if (canonical != null && canonical !in referenced) {
          file.delete()
        }
      }
    }
  }

  private fun migrateFromLegacyPreferences(): CafeLogSnapshot {
    val active = readLegacyList("entries")
    val archived = readLegacyList("archived_entries")
    return CafeLogSnapshot(active = active, archived = archived)
  }

  private fun readLegacyList(key: String): List<CafeEntry> = runCatching {
    val array = JSONArray(legacyPreferences.getString(key, "[]"))
    (0 until array.length()).mapNotNull { index ->
      runCatching {
        val item = array.optJSONObject(index) ?: return@runCatching null
        val primaryPhoto = item.getString("photoPath")
        val photos = item.optJSONArray("photoPaths")?.let { arr ->
          (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.orEmpty().ifEmpty { listOf(primaryPhoto) }
        sanitizePhotos(
          CafeEntry(
            id = item.getString("id"),
            photoPath = primaryPhoto,
            venue = item.getString("venue"),
            address = item.optString("address"),
            visitedAt = item.getLong("visitedAt"),
            latitude = item.optionalDouble("latitude"),
            longitude = item.optionalDouble("longitude"),
            photoPaths = photos,
            manualTags = item.optJSONArray("manualTags").toStringList(),
            autoTags = item.optJSONArray("autoTags").toStringList(),
            taggingModelVersion = item.optString("taggingModelVersion").takeIf(String::isNotBlank),
            classificationRuns = item.optJSONArray("classificationRuns").toClassificationRuns(),
          ),
        )
      }.onFailure { error ->
        CafeLog.w("LocalCafeLogRepository", "Skipping corrupt legacy entry at index $index", error)
      }.getOrNull()
    }.sortedByDescending { it.visitedAt }
  }.getOrDefault(emptyList())

  private fun JSONObject.optionalDouble(key: String): Double? =
    if (isNull(key) || !has(key)) null else optDouble(key)

  private fun JSONArray?.toStringList(): List<String> =
    this?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }.orEmpty()

  private fun JSONArray?.toClassificationRuns(): List<ClassificationRun> = this?.let { array ->
    (0 until array.length()).mapNotNull { index ->
      val item = array.optJSONObject(index) ?: return@mapNotNull null
      val labels = item.optJSONArray("labels").let { tags ->
        if (tags == null) {
          emptyList()
        } else {
          (0 until tags.length()).mapNotNull { tagIndex ->
            val tag = tags.optJSONObject(tagIndex) ?: return@mapNotNull null
            FoodLabel(tag.optString("name"), tag.optDouble("confidence").toFloat())
          }
        }
      }
      ClassificationRun(
        item.optLong("occurredAt"),
        item.optString("modelVersion"),
        item.optInt("photoCount"),
        item.optLong("durationMillis"),
        item.optString("outcome"),
        labels,
        item.optString("errorMessage").takeIf(String::isNotBlank),
      )
    }
  }.orEmpty()
}
