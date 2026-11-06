package com.teknosolvo.personalhub

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration

class PersonalHubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()

        // OpenStreetMap tile usage policy requires an identifying user agent.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = cacheDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
        }

        // Apply vault security settings (auto-lock) whenever they change.
        container.scope.launch {
            container.vault.securityFlow.collectLatest { container.vault.applySessionSettings(it) }
        }

        container.applyTrackingSchedule()
        container.requestGeofenceSync()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.scope.launch {
                    container.tracker.reportHealth(container.locations.health().issue)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                // Lock the vault as soon as the app leaves the foreground.
                container.session.onAppBackground()
            }
        })
    }
}
