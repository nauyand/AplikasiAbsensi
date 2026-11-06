package com.teknosolvo.personalhub.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.teknosolvo.personalhub.tracking.TrackerHealth

/** Live tracker health, refreshed on every resume (permissions can change in system settings). */
@Composable
fun rememberTrackerHealth(): TrackerHealth {
    val container = LocalContainer.current
    var health by remember { mutableStateOf(container.locations.health()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        health = container.locations.health()
        container.requestGeofenceSync()
    }
    return health
}

class PermissionActions(
    val requestForeground: () -> Unit,
    val requestBackground: () -> Unit,
    val requestNotifications: () -> Unit,
    val openAppSettings: () -> Unit,
    val openLocationSettings: () -> Unit,
)

/**
 * Location permission flow that follows Android 10–15 rules:
 * 1. foreground (fine + coarse) via the runtime dialog;
 * 2. background ("Allow all the time") as a separate request after an explanation — on Android 11+
 *    the system shows its settings page instead of a dialog.
 */
@Composable
fun rememberPermissionActions(onChanged: () -> Unit = {}): PermissionActions {
    val context = LocalContext.current
    val container = LocalContainer.current
    var showBackgroundRationale by remember { mutableStateOf(false) }

    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.requestGeofenceSync(); onChanged()
    }
    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        container.requestGeofenceSync(); onChanged()
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !container.locations.hasBackground()
        ) showBackgroundRationale = true
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    if (showBackgroundRationale && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AlertDialog(
            onDismissRequest = { showBackgroundRationale = false },
            title = { Text("Izinkan lokasi sepanjang waktu") },
            text = {
                Text(
                    "Agar kedatangan dan kepulangan tercatat otomatis saat layar mati atau aplikasi ditutup, " +
                        "pilih \"Izinkan sepanjang waktu\" pada halaman berikutnya.\n\n" +
                        "Data lokasi hanya disimpan di perangkat ini dan tidak dikirim ke server mana pun."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Lanjutkan") }
            },
            dismissButton = { TextButton(onClick = { showBackgroundRationale = false }) { Text("Nanti") } },
        )
    }

    return remember {
        PermissionActions(
            requestForeground = {
                foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            requestBackground = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (!container.locations.hasFine()) {
                        foreground.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    } else showBackgroundRationale = true
                }
            },
            requestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            },
            openAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            },
            openLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        )
    }
}

/** Human-readable fix for a [TrackerHealth.issue]. */
fun PermissionActions.fixFor(issue: String?): Pair<String, () -> Unit>? = when (issue) {
    TrackerHealth.ISSUE_NO_PERMISSION -> "Izinkan lokasi" to requestForeground
    TrackerHealth.ISSUE_GPS_OFF -> "Nyalakan GPS" to openLocationSettings
    TrackerHealth.ISSUE_NO_BACKGROUND -> "Izinkan sepanjang waktu" to requestBackground
    TrackerHealth.ISSUE_PLAY_SERVICES -> "Buka pengaturan" to openAppSettings
    else -> null
}
