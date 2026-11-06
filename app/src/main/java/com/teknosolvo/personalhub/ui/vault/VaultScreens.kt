package com.teknosolvo.personalhub.ui.vault

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.VaultCategory
import com.teknosolvo.personalhub.ui.components.ConfirmDialog
import com.teknosolvo.personalhub.ui.components.EmptyState
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.IconBadge
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.categoryIcon
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.util.dateTime
import com.teknosolvo.personalhub.vault.GeneratorOptions
import com.teknosolvo.personalhub.vault.PasswordGenerator
import com.teknosolvo.personalhub.vault.Strength
import com.teknosolvo.personalhub.vault.VaultAccount
import com.teknosolvo.personalhub.vault.VaultSecret
import kotlinx.coroutines.launch

/** Copies through [com.teknosolvo.personalhub.vault.SecureClipboard] with the configured auto-clear. */
@Composable
fun rememberCopier(): (label: String, value: String, sensitive: Boolean) -> Unit {
    val c = LocalContainer.current
    val context = LocalContext.current
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(null)
    return { label, value, sensitive ->
        val secs = security?.clipboardClearSeconds ?: 30
        c.clipboard.copy("NAUY Hub · $label", value, sensitive, if (sensitive) secs else 0)
        context.toast(if (sensitive && secs > 0) "$label disalin · clipboard dihapus dalam $secs detik" else "$label disalin")
    }
}

private const val FAV = -2L
private const val ALL = -1L

@Composable
fun VaultHomeScreen(onOpen: (Long) -> Unit, onAdd: () -> Unit, onGenerator: () -> Unit, onSettings: () -> Unit) {
    val c = LocalContainer.current
    val accounts by c.vault.accounts.collectAsStateWithLifecycle(emptyList())
    val categories by c.vault.categories.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(ALL) }
    val copy = rememberCopier()
    val catById = categories.associateBy { it.id }

    val shown = accounts.filter { a ->
        (filter == ALL || (filter == FAV && a.favorite) || a.categoryId == filter) &&
            (query.isBlank() || listOf(a.secret.title, a.secret.username, a.secret.email, a.secret.url, a.secret.notes)
                .any { it.contains(query, ignoreCase = true) } || catById[a.categoryId]?.name?.contains(query, true) == true)
    }.sortedWith(compareByDescending<VaultAccount> { it.favorite }.thenBy { it.secret.title.lowercase() })

    HubScaffold(
        title = "Private Vault",
        subtitle = "${accounts.size} akun · terenkripsi",
        actions = {
            IconButton(onClick = onGenerator) { Icon(Icons.Filled.Password, "Generator") }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Vault settings") }
            IconButton(onClick = { c.session.lock() }) { Icon(Icons.Filled.Lock, "Kunci sekarang") }
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Tambah akun") } },
    ) { pad ->
        LazyColumn(contentPadding = PaddingValues(16.dp, pad.calculateTopPadding(), 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Cari akun, username, email…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(filter == ALL, { filter = ALL }, label = { Text("Semua") }) }
                    item {
                        FilterChip(filter == FAV, { filter = FAV }, label = { Text("Favorit") },
                            leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(16.dp)) })
                    }
                    items(categories.filter { cat -> accounts.any { it.categoryId == cat.id } || filter == cat.id }, key = { it.id }) { cat ->
                        FilterChip(filter == cat.id, { filter = if (filter == cat.id) ALL else cat.id }, label = { Text(cat.name) },
                            leadingIcon = { Icon(categoryIcon(cat.iconKey), null, Modifier.size(16.dp)) })
                    }
                }
            }
            if (shown.isEmpty()) item {
                EmptyState(Icons.Filled.Key, if (accounts.isEmpty()) "Vault masih kosong" else "Tidak ditemukan",
                    if (accounts.isEmpty()) "Simpan username & password akunmu. Semua data dienkripsi di perangkat." else "Coba kata kunci atau filter lain.")
            }
            items(shown, key = { it.id }) { a ->
                val cat = catById[a.categoryId]
                HubCard(onClick = { onOpen(a.id) }, padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(categoryIcon(cat?.iconKey), MaterialTheme.colorScheme.primary, size = 42.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.secret.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                if (a.favorite) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Star, null, tint = Hub.extras.warning, modifier = Modifier.size(16.dp)) }
                            }
                            Text(a.secret.username.ifBlank { a.secret.email }.ifBlank { cat?.name ?: "" },
                                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (a.secret.password.isNotEmpty()) IconButton(onClick = { copy("Password", a.secret.password, true) }) {
                            Icon(Icons.Filled.ContentCopy, "Salin password")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretField(label: String, value: String, sensitive: Boolean = false, monospace: Boolean = false, extra: (@Composable () -> Unit)? = null) {
    if (value.isBlank()) return
    val copy = rememberCopier()
    var visible by remember { mutableStateOf(false) }
    HubCard(padding = 14.dp) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sensitive && !visible) "•".repeat(value.length.coerceIn(8, 16)) else value,
                style = MaterialTheme.typography.bodyLarge.let { if (monospace || sensitive) it.copy(fontFamily = FontFamily.Monospace) else it },
                modifier = Modifier.weight(1f),
            )
            if (sensitive) IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (visible) "Sembunyikan" else "Tampilkan")
            }
            extra?.invoke()
            IconButton(onClick = { copy(label, value, sensitive) }) { Icon(Icons.Filled.ContentCopy, "Salin $label") }
        }
    }
}

@Composable
fun AccountDetailScreen(id: Long, onBack: () -> Unit, onEdit: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by c.vault.accounts.collectAsStateWithLifecycle(null)
    val categories by c.vault.categories.collectAsStateWithLifecycle(emptyList())
    var confirmDelete by remember { mutableStateOf(false) }
    val a = accounts?.firstOrNull { it.id == id }
    if (accounts != null && a == null) { LaunchedEffect(Unit) { onBack() }; return }
    if (a == null) return
    val cat = categories.firstOrNull { it.id == a.categoryId }

    HubScaffold(
        title = a.secret.title,
        subtitle = cat?.name,
        onBack = onBack,
        actions = {
            IconButton(onClick = { scope.launch { c.vault.setFavorite(a.id, !a.favorite) } }) {
                Icon(if (a.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorit", tint = if (a.favorite) Hub.extras.warning else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Ubah") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Hapus", tint = Hub.extras.danger) }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SecretField("Username", a.secret.username)
            SecretField("Email", a.secret.email)
            SecretField("Password", a.secret.password, sensitive = true)
            if (a.secret.password.isNotEmpty()) {
                val st = PasswordGenerator.strength(a.secret.password)
                StrengthMeter(st, PasswordGenerator.entropyBits(a.secret.password))
            }
            SecretField("URL login", a.secret.url, extra = {
                IconButton(onClick = {
                    val url = a.secret.url.let { if (it.startsWith("http")) it else "https://$it" }
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Buka") }
            })
            SecretField("Catatan", a.secret.notes)
            Text("Dibuat ${a.createdAt.dateTime()} · Diperbarui ${a.updatedAt.dateTime()}",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
    }
    if (confirmDelete) ConfirmDialog("Hapus akun?", "\"${a.secret.title}\" akan dihapus permanen dari vault.",
        onConfirm = { scope.launch { c.vault.delete(a.id); context.toast("Akun dihapus"); onBack() } }, onDismiss = { confirmDelete = false })
}

@Composable
fun StrengthMeter(s: Strength, bits: Int) {
    val color = when (s) {
        Strength.VERY_WEAK, Strength.WEAK -> Hub.extras.danger
        Strength.FAIR -> Hub.extras.warning
        else -> Hub.extras.success
    }
    Column {
        LinearProgressIndicator(progress = { (s.ordinal + 1) / 5f }, color = color, modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh, drawStopIndicator = {})
        Spacer(Modifier.height(4.dp))
        Text("Kekuatan: ${s.label} · ~$bits bit", style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(id: Long, onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val categories by c.vault.categories.collectAsStateWithLifecycle(emptyList())
    var original by remember { mutableStateOf<VaultAccount?>(null) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var favorite by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id > 0) c.vault.account(id)?.let { a ->
            original = a; title = a.secret.title; username = a.secret.username; email = a.secret.email
            password = a.secret.password; url = a.secret.url; notes = a.secret.notes; categoryId = a.categoryId; favorite = a.favorite
        }
    }
    LaunchedEffect(categories) { if (categoryId == null && id == 0L) categoryId = categories.lastOrNull { it.name == "Other" }?.id }

    HubScaffold(if (id == 0L) "Akun baru" else "Ubah akun", onBack = onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("Nama aplikasi / website *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SectionLabel("Kategori")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat: VaultCategory ->
                    FilterChip(categoryId == cat.id, { categoryId = cat.id }, label = { Text(cat.name) },
                        leadingIcon = { Icon(categoryIcon(cat.iconKey), null, Modifier.size(16.dp)) })
                }
            }
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(
                password, { password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                trailingIcon = {
                    Row {
                        IconButton(onClick = { password = PasswordGenerator.generate(GeneratorOptions()); showPassword = true }) {
                            Icon(Icons.Filled.AutoFixHigh, "Generate")
                        }
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Tampilkan")
                        }
                    }
                },
            )
            if (password.isNotEmpty()) StrengthMeter(PasswordGenerator.strength(password), PasswordGenerator.entropyBits(password))
            OutlinedTextField(url, { url = it }, label = { Text("URL login") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(notes, { notes = it }, label = { Text("Catatan tambahan") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Favorit", modifier = Modifier.weight(1f)); Switch(favorite, { favorite = it })
            }
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    scope.launch {
                        val o = original
                        c.vault.save(
                            VaultAccount(
                                id = o?.id ?: 0, categoryId = categoryId, favorite = favorite,
                                secret = VaultSecret(title.trim(), username.trim(), email.trim(), password, url.trim(), notes.trim()),
                            )
                        )
                        context.toast("Tersimpan terenkripsi")
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Simpan") }
            original?.let { Text("Terakhir diperbarui ${it.updatedAt.dateTime()}", style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun GeneratorScreen(onBack: () -> Unit) {
    var length by rememberSaveable { mutableStateOf(20f) }
    var upper by rememberSaveable { mutableStateOf(true) }
    var lower by rememberSaveable { mutableStateOf(true) }
    var digits by rememberSaveable { mutableStateOf(true) }
    var symbols by rememberSaveable { mutableStateOf(true) }
    var ambiguous by rememberSaveable { mutableStateOf(false) }
    val opts = GeneratorOptions(length.toInt(), upper, lower, digits, symbols, ambiguous)
    var result by remember { mutableStateOf(runCatching { PasswordGenerator.generate(opts) }.getOrDefault("")) }
    val copy = rememberCopier()
    fun regen() { result = runCatching { PasswordGenerator.generate(opts) }.getOrDefault("") }

    HubScaffold("Password Generator", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HubCard {
                Text(result.ifEmpty { "Pilih minimal satu jenis karakter" }, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                Spacer(Modifier.height(10.dp))
                if (result.isNotEmpty()) StrengthMeter(PasswordGenerator.strength(result), PasswordGenerator.entropyBits(result))
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::regen, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.AutoFixHigh, null); Spacer(Modifier.width(6.dp)); Text("Generate") }
                    Button(onClick = { copy("Password", result, true) }, enabled = result.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Salin")
                    }
                }
            }
            SectionLabel("Panjang: ${length.toInt()} karakter")
            Slider(length, { length = it; regen() }, valueRange = 8f..64f, steps = 55)
            Toggle("Huruf kapital (A–Z)", upper) { upper = it; regen() }
            Toggle("Huruf kecil (a–z)", lower) { lower = it; regen() }
            Toggle("Angka (0–9)", digits) { digits = it; regen() }
            Toggle("Simbol (!@#…)", symbols) { symbols = it; regen() }
            Toggle("Hindari karakter mirip (I, l, 1, O, 0)", ambiguous) { ambiguous = it; regen() }
            Text("Dibuat dengan SecureRandom (CSPRNG) dan dijamin memuat setiap jenis karakter yang dipilih.",
                style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
    }
}

@Composable
fun Toggle(label: String, value: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Hub.extras.muted)
        }
        Switch(value, onChange)
    }
}
