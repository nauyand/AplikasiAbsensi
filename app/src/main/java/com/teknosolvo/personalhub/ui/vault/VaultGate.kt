package com.teknosolvo.personalhub.ui.vault

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.data.SecuritySettings
import com.teknosolvo.personalhub.ui.components.LocalContainer
import com.teknosolvo.personalhub.ui.components.PinDots
import com.teknosolvo.personalhub.ui.components.PinPad
import com.teknosolvo.personalhub.ui.components.SecureWindow
import com.teknosolvo.personalhub.ui.components.findActivity
import com.teknosolvo.personalhub.ui.components.toast
import com.teknosolvo.personalhub.ui.theme.Hub
import com.teknosolvo.personalhub.vault.UnlockResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

fun canUseBiometric(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun biometricStatusText(context: Context): String = when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
    BiometricManager.BIOMETRIC_SUCCESS -> "Tersedia"
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Belum ada sidik jari terdaftar di perangkat"
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Perangkat tidak memiliki sensor biometrik kuat"
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Sensor sedang tidak tersedia"
    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Perlu pembaruan keamanan"
    else -> "Tidak tersedia"
}

/** BiometricPrompt bound to a Keystore cipher (CryptoObject) – the cipher is only usable after success. */
fun promptBiometric(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cipher: Cipher,
    onSuccess: (Cipher) -> Unit,
    onError: (String) -> Unit,
) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            val c = result.cryptoObject?.cipher
            if (c != null) onSuccess(c) else onError("Cipher tidak tersedia")
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON && errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_CANCELED
            ) onError(errString.toString())
        }
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText("Gunakan PIN")
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setConfirmationRequired(false)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

/** Shows [content] only when the vault is unlocked; otherwise PIN setup or unlock. */
@Composable
fun VaultGate(content: @Composable () -> Unit) {
    val c = LocalContainer.current
    val unlocked by c.session.unlocked.collectAsStateWithLifecycle()
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(initialValue = LOADING)
    when {
        security === LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        security == null -> PinSetupScreen()
        !unlocked -> UnlockScreen(security!!, title = "Private Vault")
        else -> {
            SecureWindow(security?.screenshotProtection != false)
            content()
        }
    }
}

/** Optional lock for the whole app (Settings → PIN & Biometrics → "Kunci seluruh aplikasi"). */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val c = LocalContainer.current
    val unlocked by c.session.unlocked.collectAsStateWithLifecycle()
    val security by c.vault.securityFlow.collectAsStateWithLifecycle(initialValue = LOADING)
    val s = security
    if (s != null && s !== LOADING && s.appLockEnabled && !unlocked) {
        SecureWindow(true)
        UnlockScreen(s, title = "NAUY Hub terkunci")
    } else content()
}

private val LOADING = SecuritySettings(pinSalt = ByteArray(0), pinIterations = 0, pinLength = 0, pinWrappedDek = ByteArray(0),
    createdAt = -1, updatedAt = -1)

@Composable
fun UnlockScreen(security: SecuritySettings, title: String) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    val lockedUntil = security.lockoutUntil
    val bioAvailable = security.biometricEnabled && canUseBiometric(context) && activity != null

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(1000) }
        now = System.currentTimeMillis()
    }

    fun biometric() {
        if (activity == null) return
        scope.launch {
            val cipher = c.vault.biometricUnlockCipher()
            if (cipher == null) {
                message = "Biometrik dinonaktifkan karena sidik jari perangkat berubah. Masuk dengan PIN lalu aktifkan lagi."
                return@launch
            }
            promptBiometric(activity, title, "Gunakan sidik jari untuk membuka", cipher,
                onSuccess = { authorized ->
                    scope.launch {
                        val r = c.vault.completeBiometricUnlock(authorized)
                        if (r is UnlockResult.Error) message = r.message
                    }
                },
                onError = { message = it })
        }
    }

    LaunchedEffect(Unit) { if (bioAvailable && lockedUntil <= System.currentTimeMillis()) biometric() }

    fun submit() {
        busy = true
        val chars = pin.toCharArray()
        pin = ""
        scope.launch {
            val r = withContext(Dispatchers.Default) { c.vault.unlockWithPin(chars) }
            busy = false
            when (r) {
                is UnlockResult.WrongPin -> { error = true; message = "PIN salah · ${r.attemptsLeft} percobaan lagi sebelum dikunci sementara" }
                is UnlockResult.LockedOut -> { error = true; message = null }
                is UnlockResult.Error -> message = "Gagal membuka: ${r.message}"
                else -> {}
            }
        }
    }

    val locked = lockedUntil > now
    LockLayout(title, if (locked) "Terlalu banyak percobaan. Coba lagi dalam ${(lockedUntil - now) / 1000 + 1} detik."
        else message ?: "Masukkan PIN ${security.pinLength} digit") {
        PinDots(security.pinLength, pin.length, error)
        Spacer(Modifier.height(28.dp))
        if (busy) CircularProgressIndicator(Modifier.size(28.dp)) else Spacer(Modifier.size(28.dp))
        Spacer(Modifier.height(12.dp))
        PinPad(
            enabled = !busy && !locked,
            onDigit = { d ->
                error = false
                if (pin.length < security.pinLength) {
                    pin += d
                    if (pin.length == security.pinLength) submit()
                }
            },
            onBackspace = { pin = pin.dropLast(1) },
            onBiometric = if (bioAvailable) ({ biometric() }) else null,
        )
    }
}

@Composable
fun PinSetupScreen() {
    val c = LocalContainer.current
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val scope = rememberCoroutineScope()
    var length by remember { mutableIntStateOf(6) }
    var first by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun finish(p: String) {
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.Default) { c.vault.setupPin(p.toCharArray()) }
                context.toast("Vault dibuat dan terenkripsi")
                // Offer biometrics right away.
                if (activity != null && canUseBiometric(context)) {
                    c.vault.biometricEnrollCipher()?.let { cipher ->
                        promptBiometric(activity, "Aktifkan sidik jari", "Buka vault dengan biometrik", cipher,
                            onSuccess = { ok -> scope.launch { c.vault.completeBiometricEnroll(ok); context.toast("Biometrik aktif") } },
                            onError = { context.toast(it) })
                    }
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    LockLayout(
        "Buat PIN Vault",
        error ?: if (first == null) "PIN dipakai untuk membuka vault dan sebagai cadangan sidik jari." else "Ulangi PIN untuk konfirmasi",
    ) {
        if (first == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(4, 6, 8).forEach { n -> FilterChip(length == n, { length = n; pin = "" }, label = { Text("$n digit") }) }
            }
            Spacer(Modifier.height(16.dp))
        }
        PinDots(length, pin.length, error != null)
        Spacer(Modifier.height(28.dp))
        if (busy) CircularProgressIndicator() else
            PinPad(
                onDigit = { d ->
                    error = null
                    if (pin.length < length) pin += d
                    if (pin.length == length) {
                        val f = first
                        if (f == null) { first = pin; pin = "" }
                        else if (f == pin) { finish(pin); pin = "" }
                        else { error = "PIN tidak sama. Ulangi dari awal."; first = null; pin = "" }
                    }
                },
                onBackspace = { pin = pin.dropLast(1) },
            )
        if (first != null) TextButton(onClick = { first = null; pin = "" }) { Text("Mulai ulang") }
    }
}

@Composable
private fun LockLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).clip(MaterialTheme.shapes.large).background(Hub.extras.accentGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Hub.extras.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        content()
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, null, tint = Hub.extras.muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text("AES-256-GCM · Android Keystore", style = MaterialTheme.typography.labelSmall, color = Hub.extras.muted)
        }
    }
}
