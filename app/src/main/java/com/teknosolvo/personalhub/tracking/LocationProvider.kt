package com.teknosolvo.personalhub.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.teknosolvo.personalhub.util.LOCALE_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Snapshot of everything that can stop the tracker from working. */
data class TrackerHealth(
    val fineLocation: Boolean,
    val coarseLocation: Boolean,
    val backgroundLocation: Boolean,
    val locationEnabled: Boolean,
    val playServices: Boolean,
) {
    /** Most important problem, as a stable key; null when everything is fine. */
    val issue: String?
        get() = when {
            !playServices -> ISSUE_PLAY_SERVICES
            !fineLocation && !coarseLocation -> ISSUE_NO_PERMISSION
            !locationEnabled -> ISSUE_GPS_OFF
            !backgroundLocation -> ISSUE_NO_BACKGROUND
            else -> null
        }

    companion object {
        const val ISSUE_PLAY_SERVICES = "PLAY_SERVICES"
        const val ISSUE_NO_PERMISSION = "NO_PERMISSION"
        const val ISSUE_GPS_OFF = "GPS_OFF"
        const val ISSUE_NO_BACKGROUND = "NO_BACKGROUND"

        fun describe(issue: String?): String = when (issue) {
            null -> "Semua siap. Deteksi otomatis aktif."
            ISSUE_PLAY_SERVICES -> "Google Play Services tidak tersedia; geofencing tidak dapat berjalan."
            ISSUE_NO_PERMISSION -> "Izin lokasi belum diberikan atau dicabut."
            ISSUE_GPS_OFF -> "Layanan lokasi (GPS) sedang mati."
            ISSUE_NO_BACKGROUND -> "Izin lokasi latar belakang (\"Izinkan sepanjang waktu\") belum diberikan. Deteksi hanya berjalan saat aplikasi dibuka."
            else -> issue
        }
    }
}

class LocationProvider(private val context: Context) {

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    fun hasFine() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasCoarse() = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasAnyLocation() = hasFine() || hasCoarse()
    fun hasBackground() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else hasAnyLocation()

    fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    fun hasPlayServices(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    fun health() = TrackerHealth(
        fineLocation = hasFine(),
        coarseLocation = hasCoarse(),
        backgroundLocation = hasBackground(),
        locationEnabled = isLocationEnabled(),
        playServices = hasPlayServices(),
    )

    /**
     * One-shot location fix. Uses the fused provider; returns null when no permission,
     * location is off, or nothing arrives within [timeoutMs].
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    suspend fun currentFix(highAccuracy: Boolean, timeoutMs: Long = 30_000): Location? {
        if (!hasAnyLocation() || !isLocationEnabled()) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(30_000)
            .setDurationMillis(timeoutMs)
            .build()
        val cts = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMs + 5_000) { client.getCurrentLocation(request, cts.token).await(cts) }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** Reverse geocoding; null when offline or unavailable. Optional, used only for labels. */
    suspend fun address(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            @Suppress("DEPRECATION")
            Geocoder(context, LOCALE_ID).getFromLocation(lat, lng, 1)?.firstOrNull()?.getAddressLine(0)
        }.getOrNull()
    }

    companion object {
        fun isMock(location: Location): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) location.isMock
            else @Suppress("DEPRECATION") location.isFromMockProvider

        fun Location.toFix() = Fix(
            lat = latitude,
            lng = longitude,
            accuracyM = if (hasAccuracy()) accuracy else 999f,
            // Location.time is the wall clock of the fix; never accept a fix from the future.
            time = System.currentTimeMillis().let { now -> if (time in 1..now) time else now },
            mock = isMock(this),
        )
    }
}
