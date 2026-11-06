package com.teknosolvo.personalhub.vault

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.crypto.SecretKey

/**
 * Holds the decrypted vault data key in memory only while unlocked.
 * Locks on inactivity ([autoLockSeconds]) and when the app goes to the background.
 */
class VaultSession(private val scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis) {
    private var keyBytes: ByteArray? = null
    private var key: SecretKey? = null
    private var lastActivity = 0L
    private var timer: Job? = null
    @Volatile private var holds = 0

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** 0 = only lock on background / manually. */
    @Volatile var autoLockSeconds: Int = 60
    @Volatile var lockOnBackground: Boolean = true

    @Synchronized
    fun open(dek: ByteArray) {
        keyBytes?.fill(0)
        keyBytes = dek.copyOf()
        key = CryptoBox.keyOf(keyBytes!!)
        dek.fill(0)
        lastActivity = clock()
        _unlocked.value = true
        startTimer()
    }

    @Synchronized
    fun lock() {
        keyBytes?.fill(0)
        keyBytes = null
        key = null
        _unlocked.value = false
        timer?.cancel()
        timer = null
    }

    @Synchronized
    fun requireKey(): SecretKey = key ?: throw VaultLockedException()

    /** Raw key copy (for re-wrapping with a new PIN / biometrics). Caller must wipe it. */
    @Synchronized
    fun copyKeyBytes(): ByteArray = keyBytes?.copyOf() ?: throw VaultLockedException()

    fun touch() {
        if (_unlocked.value) lastActivity = clock()
    }

    /** Keep the vault open while an external screen (file picker) is shown. */
    fun hold() { holds++ }
    fun release() { holds = (holds - 1).coerceAtLeast(0); touch() }

    fun onAppBackground() {
        if (lockOnBackground && holds == 0) lock()
    }

    /** Check inactivity now (also called by the timer). */
    fun checkTimeout() {
        val s = autoLockSeconds
        if (_unlocked.value && s > 0 && holds == 0 && clock() - lastActivity >= s * 1000L) lock()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = scope.launch {
            while (isActive) {
                delay(5_000)
                checkTimeout()
            }
        }
    }
}

class VaultLockedException : IllegalStateException("Vault terkunci")
