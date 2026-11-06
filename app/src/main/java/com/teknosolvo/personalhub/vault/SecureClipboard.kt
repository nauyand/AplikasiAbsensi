package com.teknosolvo.personalhub.vault

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies sensitive text, flags it as sensitive (Android 13+ hides it from the clipboard preview)
 * and clears it automatically after a delay if it is still the current clip.
 */
class SecureClipboard(private val context: Context, private val scope: CoroutineScope) {
    private var clearJob: Job? = null

    fun copy(label: String, text: String, sensitive: Boolean, clearAfterSeconds: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) {
            clip.description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
        cm.setPrimaryClip(clip)
        clearJob?.cancel()
        if (clearAfterSeconds > 0) {
            val marker = label
            clearJob = scope.launch {
                delay(clearAfterSeconds * 1000L)
                runCatching {
                    val current = cm.primaryClipDescription
                    if (current == null || current.label == marker) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
                        else cm.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }
    }
}
