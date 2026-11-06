package com.teknosolvo.personalhub.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.NotificationType
import com.teknosolvo.personalhub.ui.components.ConfirmDialog
import com.teknosolvo.personalhub.ui.components.EmptyState
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.IconBadge
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.StatusDot
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.util.relativeTime
import kotlinx.coroutines.launch

@Composable
fun NotificationCenterScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val items by c.db.notifications().observeAll().collectAsStateWithLifecycle(emptyList())
    var confirmClear by remember { mutableStateOf(false) }
    // Opening the center marks everything as read (after the list is shown once).
    LaunchedEffect(Unit) { c.db.notifications().markAllRead() }

    HubScaffold("Notifikasi", onBack = onBack, actions = {
        if (items.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Filled.DeleteSweep, "Hapus semua") }
    }) { pad ->
        if (items.isEmpty()) {
            EmptyState(Icons.Filled.NotificationsNone, "Belum ada notifikasi",
                "Notifikasi masuk/keluar lokasi dan masalah GPS akan muncul di sini.", Modifier.padding(pad))
        } else LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { n ->
                val (icon, tint) = when (n.type) {
                    NotificationType.ENTER -> Icons.AutoMirrored.Filled.Login to Hub.extras.success
                    NotificationType.EXIT -> Icons.AutoMirrored.Filled.Logout to MaterialTheme.colorScheme.secondary
                    NotificationType.GPS -> Icons.Filled.GpsOff to Hub.extras.danger
                    NotificationType.PERMISSION -> Icons.Filled.LocationDisabled to Hub.extras.danger
                    else -> Icons.Filled.WarningAmber to Hub.extras.warning
                }
                HubCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon, tint, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall)
                            Text(n.body, style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
                            Text(relativeTime(n.createdAt), style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted)
                        }
                        if (!n.read) StatusDot(MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (confirmClear) ConfirmDialog("Hapus semua notifikasi?", "Riwayat notifikasi akan dikosongkan.",
        onConfirm = { scope.launch { c.db.notifications().deleteAll() } }, onDismiss = { confirmClear = false })
}
