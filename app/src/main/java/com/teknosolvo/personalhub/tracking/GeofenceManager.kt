package com.teknosolvo.personalhub.tracking

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.teknosolvo.personalhub.data.Place
import kotlinx.coroutines.tasks.await

/**
 * Registers one OS geofence per enabled place. The OS geofences only *wake* the app; the
 * [PresenceEngine] decides enter/exit using the exact radius, so the OS radius is padded.
 */
class GeofenceManager(private val context: Context, private val locations: LocationProvider) {

    private val client by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        PendingIntent.getBroadcast(context, 0, Intent(context, GeofenceReceiver::class.java), flags)
    }

    sealed interface SyncResult {
        data class Ok(val count: Int) : SyncResult
        data class Skipped(val reason: String) : SyncResult
        data class Failed(val message: String) : SyncResult
    }

    @SuppressLint("MissingPermission")
    suspend fun sync(places: List<Place>, enabled: Boolean, toleranceM: Int): SyncResult {
        val health = locations.health()
        if (!health.playServices) return SyncResult.Skipped(TrackerHealth.ISSUE_PLAY_SERVICES)
        return try {
            client.removeGeofences(pendingIntent).await()
            val active = places.filter { it.enabled }.take(MAX_GEOFENCES)
            if (!enabled || active.isEmpty()) return SyncResult.Ok(0)
            if (!locations.hasFine()) return SyncResult.Skipped(TrackerHealth.ISSUE_NO_PERMISSION)
            val fences = active.map { p ->
                Geofence.Builder()
                    .setRequestId(p.id.toString())
                    .setCircularRegion(p.latitude, p.longitude, (p.radiusM + toleranceM).toFloat().coerceAtLeast(MIN_OS_RADIUS))
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setNotificationResponsiveness(RESPONSIVENESS_MS)
                    .build()
            }
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(fences)
                .build()
            client.addGeofences(request, pendingIntent).await()
            SyncResult.Ok(fences.size)
        } catch (e: SecurityException) {
            SyncResult.Skipped(TrackerHealth.ISSUE_NO_PERMISSION)
        } catch (e: Exception) {
            // ApiException 1000 = GEOFENCE_NOT_AVAILABLE (location off / "Google location accuracy" off).
            SyncResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        const val MAX_GEOFENCES = 100
        /** OS geofences below ~100 m are unreliable; the engine still uses the exact radius. */
        const val MIN_OS_RADIUS = 100f
        const val RESPONSIVENESS_MS = 60_000
    }
}
