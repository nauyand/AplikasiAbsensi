package com.teknosolvo.personalhub.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.DailyActivity
import com.teknosolvo.personalhub.data.Place
import com.teknosolvo.personalhub.data.QuickNote
import com.teknosolvo.personalhub.ui.components.EmptyState
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.SettingRow
import com.teknosolvo.personalhub.ui.components.asColor
import com.teknosolvo.personalhub.ui.components.categoryIcon
import com.teknosolvo.personalhub.ui.components.placeIcon
import com.teknosolvo.personalhub.ui.settings.UnlockVaultDialog
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.util.dateTime
import com.teknosolvo.personalhub.util.durationShort
import kotlinx.coroutines.delay

/** One search box across activity history, places, notes and (when unlocked) vault accounts. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onOpenPlace: (Long) -> Unit,
    onOpenAccount: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val c = LocalContainer.current
    var query by rememberSaveable { mutableStateOf("") }
    var activities by remember { mutableStateOf<List<DailyActivity>>(emptyList()) }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var notes by remember { mutableStateOf<List<QuickNote>>(emptyList()) }
    val accounts by c.vault.accounts.collectAsStateWithLifecycle(emptyList())
    val categories by c.vault.categories.collectAsStateWithLifecycle(emptyList())
    val unlocked by c.session.unlocked.collectAsStateWithLifecycle()
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(null)
    var unlockDialog by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        delay(200) // debounce
        val q = query.trim()
        if (q.length < 2) { activities = emptyList(); places = emptyList(); notes = emptyList(); return@LaunchedEffect }
        activities = c.db.activities().search(q)
        places = c.db.places().search(q)
        notes = c.db.notes().search(q)
    }
    val q = query.trim()
    val accountHits = if (q.length < 2) emptyList() else accounts.filter { a ->
        listOf(a.secret.title, a.secret.username, a.secret.email, a.secret.url).any { it.contains(q, true) }
    }
    val catById = categories.associateBy { it.id }

    HubScaffold("Pencarian", onBack = onBack) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Cari riwayat, lokasi, akun, catatan…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "Bersihkan") } },
                    singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (q.length < 2) {
                item { EmptyState(Icons.Filled.Search, "Pencarian global", "Ketik minimal 2 huruf. Akun vault hanya dicari saat vault terbuka.") }
                return@LazyColumn
            }
            if (places.isNotEmpty()) {
                item { SectionLabel("Lokasi (${places.size})") }
                items(places, key = { "p${it.id}" }) { p ->
                    SettingRow(placeIcon(p.iconKey), p.name, "Radius ${p.radiusM} m" + (p.address?.let { " · $it" } ?: ""), p.colorArgb.asColor(),
                        onClick = { onOpenPlace(p.id) })
                }
            }
            if (activities.isNotEmpty()) {
                item { SectionLabel("Riwayat aktivitas (${activities.size})") }
                items(activities, key = { "a${it.id}" }) { a ->
                    SettingRow(Icons.Filled.History, a.title,
                        a.startAt.dateTime() + (a.endAt?.let { " · ${durationShort(it - a.startAt)}" } ?: " · berlangsung") + (a.note?.let { " · “$it”" } ?: ""),
                        onClick = { onOpenActivity(a.id) })
                }
            }
            if (notes.isNotEmpty()) {
                item { SectionLabel("Catatan (${notes.size})") }
                items(notes, key = { "n${it.id}" }) { n ->
                    SettingRow(Icons.AutoMirrored.Filled.StickyNote2, n.title.ifBlank { n.body.take(40) }, n.body.take(80), Hub.extras.warning, onClick = { onOpenNote(n.id) })
                }
            }
            item { SectionLabel("Akun vault") }
            when {
                security == null -> item { Text("Vault belum dibuat.", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted) }
                !unlocked -> item {
                    SettingRow(Icons.Filled.Lock, "Vault terkunci", "Buka dengan PIN untuk ikut mencari akun", onClick = { unlockDialog = true })
                }
                accountHits.isEmpty() -> item { Text("Tidak ada akun yang cocok.", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted) }
                else -> items(accountHits, key = { "v${it.id}" }) { a ->
                    SettingRow(categoryIcon(catById[a.categoryId]?.iconKey), a.secret.title, a.secret.username.ifBlank { a.secret.email },
                        onClick = { onOpenAccount(a.id) })
                }
            }
            if (places.isEmpty() && activities.isEmpty() && notes.isEmpty() && accountHits.isEmpty()) item {
                Text("Tidak ada hasil untuk \"$q\".", style = MaterialTheme.typography.bodyMedium, color = Hub.extras.muted)
            }
        }
    }
    if (unlockDialog) UnlockVaultDialog { unlockDialog = false }
}
