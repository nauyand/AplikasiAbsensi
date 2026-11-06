package com.teknosolvo.personalhub.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.ui.components.HubCard
import com.teknosolvo.personalhub.ui.components.HubScaffold
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.SectionLabel
import com.teknosolvo.personalhub.ui.components.SettingRow
import com.teknosolvo.personalhub.ui.components.findActivity
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.vault.UnlockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun unlockMessage(r: UnlockResult) = when (r) {
    UnlockResult.Success -> "Berhasil"
    is UnlockResult.WrongPin -> "PIN salah (${r.attemptsLeft} percobaan tersisa)"
    is UnlockResult.LockedOut -> "Terlalu banyak percobaan, coba lagi nanti"
    UnlockResult.NotSetUp -> "Vault belum dibuat"
    is UnlockResult.Error -> r.message
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultSettingsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val scope = rememberCoroutineScope()
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(null)
    val categories by c.vault.categories.collectAsStateWithLifecycle(emptyList())
    var changePin by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf("") }
    val s = security ?: return
    val bioOk = canUseBiometric(context)

    HubScaffold("Vault Settings", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Autentikasi")
            HubCard {
                Toggle("Buka dengan sidik jari / biometrik", s.biometricEnabled,
                    subtitle = if (bioOk) "BiometricPrompt + CryptoObject (Keystore)" else biometricStatusText(context)) { on ->
                    if (!on) scope.launch { c.vault.disableBiometric(); context.toast("Biometrik dinonaktifkan") }
                    else if (bioOk && activity != null) {
                        val cipher = c.vault.biometricEnrollCipher()
                        if (cipher != null) promptBiometric(activity, "Aktifkan biometrik", "Konfirmasi sidik jari", cipher,
                            onSuccess = { ok -> scope.launch { c.vault.completeBiometricEnroll(ok); context.toast("Biometrik aktif") } },
                            onError = { context.toast(it) })
                    } else context.toast(biometricStatusText(context))
                }
                SettingRow(Icons.Filled.Pin, "Ganti PIN", "PIN saat ini ${s.pinLength} digit", onClick = { changePin = true })
                Toggle("Kunci seluruh aplikasi", s.appLockEnabled, subtitle = "Minta PIN/biometrik setiap membuka aplikasi") { on ->
                    scope.launch { c.vault.updateSecurity { it.copy(appLockEnabled = on) } }
                }
            }
            SectionLabel("Auto-lock")
            HubCard {
                Text("Kunci otomatis saat tidak digunakan", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30 to "30 dtk", 60 to "1 mnt", 300 to "5 mnt", 900 to "15 mnt", 0 to "Tidak").forEach { (sec, label) ->
                        FilterChip(s.autoLockSeconds == sec, { scope.launch { c.vault.updateSecurity { it.copy(autoLockSeconds = sec) } } }, label = { Text(label) })
                    }
                }
                Toggle("Kunci saat aplikasi ke background", s.lockOnBackground) { on ->
                    scope.launch { c.vault.updateSecurity { it.copy(lockOnBackground = on) } }
                }
            }
            SectionLabel("Privasi layar & clipboard")
            HubCard {
                Toggle("Blokir screenshot di vault", s.screenshotProtection, subtitle = "FLAG_SECURE: juga menyembunyikan isi di Recent Apps") { on ->
                    scope.launch { c.vault.updateSecurity { it.copy(screenshotProtection = on) } }
                }
                Text("Hapus clipboard otomatis setelah", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120, 0).forEach { sec ->
                        FilterChip(s.clipboardClearSeconds == sec, { scope.launch { c.vault.updateSecurity { it.copy(clipboardClearSeconds = sec) } } },
                            label = { Text(if (sec == 0) "Tidak" else "$sec dtk") })
                    }
                }
            }
            SectionLabel("Kategori")
            HubCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { cat ->
                        InputChip(
                            selected = false,
                            onClick = { if (!cat.builtin) scope.launch { c.vault.deleteCategory(cat); context.toast("Kategori ${cat.name} dihapus") } },
                            label = { Text(cat.name) },
                            trailingIcon = if (!cat.builtin) ({ Icon(Icons.Filled.Close, "Hapus", Modifier.size(16.dp)) }) else null,
                        )
                    }
                }
                Row {
                    OutlinedTextField(newCategory, { newCategory = it.take(30) }, label = { Text("Kategori baru") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    TextButton(enabled = newCategory.isNotBlank(), onClick = {
                        scope.launch { c.vault.addCategory(newCategory); newCategory = "" }
                    }) { Text("Tambah") }
                }
            }
            SectionLabel("Zona berbahaya")
            HubCard {
                SettingRow(Icons.Filled.DeleteForever, "Hapus seluruh isi vault", "Semua akun, PIN, dan kunci enkripsi dihapus",
                    tint = Hub.extras.danger, onClick = { wipe = true })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (changePin) ChangePinDialog(onDismiss = { changePin = false })
    if (wipe) PinConfirmDialog(
        title = "Hapus vault?",
        text = "Tindakan ini tidak bisa dibatalkan. Masukkan PIN untuk konfirmasi. Buat backup terenkripsi dulu jika perlu.",
        confirmLabel = "Hapus vault",
        onConfirmed = { scope.launch { c.vault.wipeVault(); context.toast("Vault dihapus"); onBack() } },
        onDismiss = { wipe = false },
    )
}

@Composable
fun PinConfirmDialog(title: String, text: String, confirmLabel: String, onConfirmed: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                Spacer(Modifier.height(12.dp))
                PinField(pin, { pin = it }, "PIN")
                error?.let { Text(it, color = Hub.extras.danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length >= 4, onClick = {
                val chars = pin.toCharArray(); pin = ""
                scope.launch {
                    val r = withContext(Dispatchers.Default) { c.vault.verifyPin(chars) }
                    if (r == UnlockResult.Success) { onConfirmed(); onDismiss() } else error = unlockMessage(r)
                }
            }) { Text(confirmLabel, color = Hub.extras.danger) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value, { v -> onChange(v.filter(Char::isDigit).take(8)) }, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf("") }
    var new1 by remember { mutableStateOf("") }
    var new2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ganti PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(old, { old = it }, "PIN lama")
                PinField(new1, { new1 = it }, "PIN baru (4–8 digit)")
                PinField(new2, { new2 = it }, "Ulangi PIN baru")
                error?.let { Text(it, color = Hub.extras.danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && old.length >= 4 && new1.length >= 4, onClick = {
                if (new1 != new2) { error = "PIN baru tidak sama"; return@TextButton }
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.Default) { c.vault.changePin(old.toCharArray(), new1.toCharArray()) }
                    busy = false
                    if (r == UnlockResult.Success) { context.toast("PIN diganti"); onDismiss() } else error = unlockMessage(r)
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
