package com.teknosolvo.personalhub

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.teknosolvo.personalhub.ui.nav.AppRoot
import com.teknosolvo.personalhub.ui.theme.HubTheme

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    private val container get() = (application as PersonalHubApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by container.settings.prefs.collectAsStateWithLifecycle()
            HubTheme(themeMode = prefs.themeMode, accent = prefs.accent) {
                AppRoot(container)
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        container.session.touch()
    }

    override fun onResume() {
        super.onResume()
        container.session.checkTimeout()
    }
}
