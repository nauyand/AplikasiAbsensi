package com.teknosolvo.personalhub.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.teknosolvo.personalhub.PersonalHubApp
import com.teknosolvo.personalhub.data.EventSource
import com.teknosolvo.personalhub.data.EventType
import com.teknosolvo.personalhub.tracking.LocationProvider.Companion.toFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val TAG = "HubTracker"

/** Receives OS geofence transitions and feeds the triggering fix into the engine. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val container = (context.applicationContext as PersonalHubApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (event.hasError()) {
                    val code = event.errorCode
                    Log.w(TAG, "Geofence error ${GeofenceStatusCodes.getStatusCodeString(code)}")
                    if (code == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
                        // Location turned off: the OS dropped all geofences. Re-sync once it is back.
                        container.tracker.reportHealth(TrackerHealth.ISSUE_GPS_OFF)
                    } else {
                        container.tracker.reportProblem("Geofence error: ${GeofenceStatusCodes.getStatusCodeString(code)}")
                    }
                    return@launch
                }
                val type = when (event.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> EventType.ENTER
                    Geofence.GEOFENCE_TRANSITION_EXIT -> EventType.EXIT
                    else -> null
                }
                val ids = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
                val location = event.triggeringLocation
                if (location != null) {
                    container.tracker.processFix(location.toFix(), EventSource.GEOFENCE, type, ids)
                } else {
                    TrackingScheduler.confirmSoon(context, 0)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Geofence processing failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Re-registers geofences after reboot or app update (the OS clears them). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> TrackingScheduler.resyncNow(context)
        }
    }
}

/** Dwell / exit-delay confirmation: takes a fresh fix and runs it through the engine. */
class ConfirmWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as PersonalHubApp).container
        c.settings.reload()
        val health = c.locations.health()
        if (health.issue == TrackerHealth.ISSUE_NO_PERMISSION || health.issue == TrackerHealth.ISSUE_GPS_OFF) {
            c.tracker.reportHealth(health.issue)
            return Result.success()
        }
        val loc = c.locations.currentFix(highAccuracy = true)
            ?: return if (runAttemptCount < 3) Result.retry() else Result.success()
        c.tracker.processFix(loc.toFix(), EventSource.CONFIRM)
        return Result.success()
    }
}

/**
 * Periodic safety net (default every 30 min): checks permissions / GPS, re-registers geofences
 * (recovers from reboot, Play services data reset, location toggles) and feeds a low-power fix to
 * catch transitions the OS geofence missed.
 */
class PeriodicCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as PersonalHubApp).container
        c.settings.reload()
        val prefs = c.settings.current
        val health = c.locations.health()
        c.tracker.reportHealth(health.issue)
        if (!prefs.trackingEnabled) return Result.success()
        c.syncGeofencesNow()
        if (inputData.getBoolean(KEY_SYNC_ONLY, false)) return Result.success()
        if (health.issue == null || health.issue == TrackerHealth.ISSUE_NO_BACKGROUND) {
            c.locations.currentFix(highAccuracy = false)?.let {
                c.tracker.processFix(it.toFix(), EventSource.PERIODIC)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_SYNC_ONLY = "syncOnly"
    }
}

object TrackingScheduler {
    private const val CONFIRM = "tracker-confirm"
    private const val PERIODIC = "tracker-periodic"
    private const val RESYNC = "tracker-resync"

    fun confirmSoon(context: Context, delayMs: Long) {
        val req = OneTimeWorkRequestBuilder<ConfirmWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(CONFIRM, ExistingWorkPolicy.REPLACE, req)
    }

    fun schedulePeriodic(context: Context, minutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (minutes <= 0) {
            wm.cancelUniqueWork(PERIODIC)
            return
        }
        val req = PeriodicWorkRequestBuilder<PeriodicCheckWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(false).build())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun resyncNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<PeriodicCheckWorker>()
            .setInputData(workDataOf(PeriodicCheckWorker.KEY_SYNC_ONLY to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(RESYNC, ExistingWorkPolicy.REPLACE, req)
    }
}
