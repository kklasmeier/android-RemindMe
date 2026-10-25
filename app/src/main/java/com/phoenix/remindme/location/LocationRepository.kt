package com.phoenix.remindme.location

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.phoenix.remindme.data.SavedPlaceDao
import com.phoenix.remindme.data.SavedPlaceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class PlaceCandidate(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String? = null,
    val osmId: Long? = null,
    val osmType: String? = null,
    val distanceMeters: Float? = null,
    val isSaved: Boolean = false,
)

@Singleton
class LocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val places: SavedPlaceDao,
) {
    private val cache = LinkedHashMap<String, List<PlaceCandidate>>()
    private val prefetchedSearches =
        ConcurrentHashMap<String, CompletableDeferred<List<PlaceCandidate>>>()
    private var lastNominatimRequestAt = 0L

    fun observeSavedPlaces(): Flow<List<SavedPlaceEntity>> = places.observeAll()

    suspend fun getPlace(id: Long): SavedPlaceEntity? = places.get(id)

    suspend fun findSavedPlace(query: String): SavedPlaceEntity? {
        val normalized = query.trim().lowercase(Locale.US)
        return places.getAll().firstOrNull {
            it.label.lowercase(Locale.US) == normalized ||
                it.displayName.lowercase(Locale.US) == normalized
        }
    }

    suspend fun needsPlaceOnboarding(): Boolean {
        val preferences = context.getSharedPreferences("place_onboarding", Context.MODE_PRIVATE)
        if (!preferences.getBoolean("complete", false)) return true
        val saved = places.getAll()
        val hasHome = saved.any { it.label.equals("Home", ignoreCase = true) }
        val hasWork = saved.any { it.label.equals("Work", ignoreCase = true) }
        return !hasHome || (!hasWork && !preferences.getBoolean("work_skipped", false))
    }

    fun completePlaceOnboarding(workSkipped: Boolean = false) {
        context.getSharedPreferences("place_onboarding", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("complete", true)
            .putBoolean("work_skipped", workSkipped)
            .apply()
    }

    suspend fun prefetchNearDevice(query: String) {
        if (
            query.isBlank() ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val key = query.trim().lowercase(Locale.US)
        if (prefetchedSearches.size >= 10) prefetchedSearches.clear()
        val result = CompletableDeferred<List<PlaceCandidate>>()
        if (prefetchedSearches.putIfAbsent(key, result) != null) return
        val candidates = runCatching {
            val device = lastKnownLocation()
            search(query, device?.latitude, device?.longitude)
        }.getOrDefault(emptyList())
        result.complete(candidates)
    }

    suspend fun searchNearDevice(query: String): List<PlaceCandidate> {
        val key = query.trim().lowercase(Locale.US)
        prefetchedSearches.remove(key)?.let { return it.await() }
        val device = runCatching { lastKnownLocation() }.getOrNull()
        return search(query, device?.latitude, device?.longitude)
    }

    suspend fun search(
        query: String,
        nearLatitude: Double? = null,
        nearLongitude: Double? = null,
    ): List<PlaceCandidate> = withContext(Dispatchers.IO) {
        val normalized = query.trim().lowercase(Locale.US)
            .replace(Regex("\\bkroger'?s\\b"), "kroger")
        if (normalized.isBlank()) return@withContext emptyList()
        val cacheKey = buildString {
            append(normalized)
            if (nearLatitude != null && nearLongitude != null) {
                append('|')
                append("%.2f,%.2f".format(Locale.US, nearLatitude, nearLongitude))
            }
        }
        cache[cacheKey]?.let { return@withContext it }

        val local = geocoderSearch(query, nearLatitude, nearLongitude)
        val remote = if (nearLatitude != null && nearLongitude != null || local.size < 3) {
            runCatching { nominatimSearch(query, nearLatitude, nearLongitude) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        // Map/search matches only — saved places stay on the Set Location screen list.
        val merged = (local + remote)
            .distinctBy { "${"%.5f".format(Locale.US, it.latitude)},${"%.5f".format(Locale.US, it.longitude)}" }
            .map { candidate ->
                if (nearLatitude != null && nearLongitude != null) {
                    val distance = FloatArray(1)
                    android.location.Location.distanceBetween(
                        nearLatitude,
                        nearLongitude,
                        candidate.latitude,
                        candidate.longitude,
                        distance,
                    )
                    candidate.copy(distanceMeters = distance[0], isSaved = false)
                } else candidate.copy(isSaved = false)
            }
            .sortedBy { it.distanceMeters ?: Float.MAX_VALUE }
            .take(8)
        cache[cacheKey] = merged
        if (cache.size > 30) cache.remove(cache.keys.first())
        merged
    }

    @Suppress("DEPRECATION")
    private fun geocoderSearch(
        query: String,
        nearLatitude: Double?,
        nearLongitude: Double?,
    ): List<PlaceCandidate> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        val results = if (nearLatitude != null && nearLongitude != null) {
            val span = 0.6
            geocoder.getFromLocationName(
                query,
                5,
                nearLatitude - span,
                nearLongitude - span,
                nearLatitude + span,
                nearLongitude + span,
            )
        } else {
            geocoder.getFromLocationName(query, 5)
        }.orEmpty()
        return results.map { address ->
            PlaceCandidate(
                name = address.featureName ?: address.thoroughfare ?: query,
                address = (0..address.maxAddressLineIndex)
                    .mapNotNull(address::getAddressLine)
                    .joinToString(", ")
                    .ifBlank { listOfNotNull(address.locality, address.adminArea).joinToString(", ") },
                latitude = address.latitude,
                longitude = address.longitude,
                category = address.subThoroughfare ?: address.locality,
            )
        }
    }

    private fun nominatimSearch(
        query: String,
        nearLatitude: Double?,
        nearLongitude: Double?,
    ): List<PlaceCandidate> {
        synchronized(this) {
            val wait = 1_000L - (System.currentTimeMillis() - lastNominatimRequestAt)
            if (wait > 0) Thread.sleep(wait)
            lastNominatimRequestAt = System.currentTimeMillis()
        }
        val localQuery = query
            .replace(Regex("\\bkroger'?s\\b", RegexOption.IGNORE_CASE), "Kroger")
            .replace(Regex("\\s+on\\s+", RegexOption.IGNORE_CASE), ", ")
        val encoded = URLEncoder.encode(localQuery, Charsets.UTF_8.name())
        val proximity = if (nearLatitude != null && nearLongitude != null) {
            val span = 0.6
            "&viewbox=${nearLongitude - span},${nearLatitude + span},${nearLongitude + span},${nearLatitude - span}&bounded=1"
        } else ""
        val connection = URL(
            "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=20&addressdetails=1&q=$encoded$proximity",
        ).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "RemindMe/1.0 (com.phoenix.remindme)")
            connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            if (connection.responseCode !in 200..299) return emptyList()
            val array = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val display = item.optString("display_name")
                PlaceCandidate(
                    name = item.optString("name").ifBlank { display.substringBefore(",") },
                    address = display,
                    latitude = item.getString("lat").toDouble(),
                    longitude = item.getString("lon").toDouble(),
                    category = item.optString("type").replace('_', ' '),
                    osmId = item.optLong("osm_id").takeIf { item.has("osm_id") },
                    osmType = item.optString("osm_type").uppercase().takeIf(String::isNotBlank),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun save(candidate: PlaceCandidate, radius: Float): SavedPlaceEntity {
        val existing = if (candidate.osmId != null && candidate.osmType != null) {
            places.findByOsmId(candidate.osmId, candidate.osmType)
        } else {
            val tolerance = .00001
            places.findNear(
                candidate.latitude - tolerance,
                candidate.latitude + tolerance,
                candidate.longitude - tolerance,
                candidate.longitude + tolerance,
            )
        }
        if (existing != null) {
            val updated = existing.copy(geofenceRadius = radius)
            places.update(updated)
            return updated
        }
        val place = SavedPlaceEntity(
            label = candidate.name,
            displayName = candidate.name,
            address = candidate.address,
            latitude = candidate.latitude,
            longitude = candidate.longitude,
            osmId = candidate.osmId,
            osmType = candidate.osmType,
            geofenceRadius = radius,
        )
        return place.copy(id = places.insert(place))
    }

    suspend fun savePlace(candidate: PlaceCandidate, radius: Float, label: String): SavedPlaceEntity {
        val place = save(candidate, radius)
        val updated = place.copy(
            label = label.trim().ifBlank { candidate.name },
            displayName = candidate.name,
            isSystemPlace = label.equals("Home", true) || label.equals("Work", true),
        )
        places.update(updated)
        return updated
    }

    suspend fun update(place: SavedPlaceEntity) = places.update(place)
    suspend fun delete(place: SavedPlaceEntity) = places.delete(place)

    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        val current = suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
            continuation.invokeOnCancellation { cancellation.cancel() }
        }
        if (current != null) return current
        return suspendCancellableCoroutine { continuation ->
            client.lastLocation
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }
    }
}
