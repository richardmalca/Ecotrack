package pe.ecotrack

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

internal data class WalkingRoute(val minutes: Int, val meters: Int, val line: JSONArray)

internal fun Context.hasRouteLocation() =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

internal fun Context.isRouteLocationEnabled() =
    LocationManagerCompat.isLocationEnabled(getSystemService(LocationManager::class.java))

@Suppress("MissingPermission")
internal fun Context.observeRouteLocation(onLocation: (Location) -> Unit): () -> Unit {
    if (!hasRouteLocation()) return {}
    val manager = getSystemService(LocationManager::class.java)
    val precise = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val providers = if (precise) listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        else listOf(LocationManager.NETWORK_PROVIDER)
    var last: Location? = null
    val listener = object : LocationListener {
        override fun onLocationChanged(fix: Location) {
            val previous = last
            if (previous == null || fix.elapsedRealtimeNanos >= previous.elapsedRealtimeNanos &&
                (fix.accuracy <= previous.accuracy + 25 ||
                    fix.elapsedRealtimeNanos - previous.elapsedRealtimeNanos > TimeUnit.SECONDS.toNanos(10))) {
                last = fix
                onLocation(fix)
            }
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Required by LocationListener on Android 9 and earlier")
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) = Unit
    }
    providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < TimeUnit.SECONDS.toNanos(30) }
        .minByOrNull { it.accuracy }?.let(listener::onLocationChanged)
    providers.forEach { provider ->
        runCatching { manager.requestLocationUpdates(provider, 2_000L, 2f, listener, Looper.getMainLooper()) }
    }
    return { manager.removeUpdates(listener) }
}

private val routeClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

internal suspend fun fetchWalkingRoute(from: Location, lat: Double, lng: Double): WalkingRoute =
    withContext(Dispatchers.IO) {
        val coordinates = "${from.longitude},${from.latitude};$lng,$lat"
        val request = Request.Builder()
            .url("https://routing.openstreetmap.de/routed-foot/route/v1/driving/$coordinates?overview=full&geometries=geojson")
            .header("User-Agent", "EcoTrack Android/0.1")
            .build()
        routeClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "El servicio de rutas no respondió. Inténtalo de nuevo." }
            val result = JSONObject(response.body?.string().orEmpty())
            check(result.optString("code") == "Ok") { "No se encontró una ruta a pie hacia este punto." }
            val route = result.getJSONArray("routes").getJSONObject(0)
            val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")
            val line = JSONArray()
            for (index in 0 until coordinates.length()) {
                val pair = coordinates.getJSONArray(index)
                line.put(JSONArray().put(pair.getDouble(1)).put(pair.getDouble(0)))
            }
            WalkingRoute(ceil(route.getDouble("duration") / 60).toInt().coerceAtLeast(1), route.getDouble("distance").toInt(), line)
        }
    }
