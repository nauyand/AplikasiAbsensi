package com.teknosolvo.personalhub.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.teknosolvo.personalhub.MainActivity
import com.teknosolvo.personalhub.R
import com.teknosolvo.personalhub.data.AppNotification
import com.teknosolvo.personalhub.data.NotificationDao
import com.teknosolvo.personalhub.data.NotificationType

/**
 * Posts system notifications (activity + system channels) and mirrors every one into the
 * in-app Notification Center. Never includes vault data.
 */
class Notifier(private val context: Context, private val dao: NotificationDao) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ACTIVITY, "Aktivitas lokasi", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Saat kamu tiba atau meninggalkan lokasi terdaftar"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SYSTEM, "Status GPS & izin", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "GPS mati, izin lokasi dicabut, atau masalah deteksi"
            }
        )
    }

    suspend fun post(type: String, title: String, body: String, system: Boolean = true) {
        dao.insert(AppNotification(type = type, title = title, body = body, createdAt = System.currentTimeMillis()))
        if (system) show(type, title, body)
    }

    fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun show(type: String, title: String, body: String) {
        if (!canPost()) return
        val channel = if (type == NotificationType.ENTER || type == NotificationType.EXIT) CH_ACTIVITY else CH_SYSTEM
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_hub)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        val id = if (channel == CH_SYSTEM) ID_SYSTEM + type.hashCode() % 100 else ID_ACTIVITY
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between check and notify.
        }
    }

    companion object {
        const val CH_ACTIVITY = "activity"
        const val CH_SYSTEM = "system"
        private const val ID_ACTIVITY = 1001
        private const val ID_SYSTEM = 2000
    }
}
