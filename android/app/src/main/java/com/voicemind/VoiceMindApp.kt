package com.voicemind

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.google.firebase.auth.FirebaseAuth
import com.voicemind.widget.RecordingWidget
import com.voicemind.widget.RecordingWidgetStateKeys
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class VoiceMindApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        observeAuthForWidget()
        observeAppForegroundForWidget()
    }

    /**
     * Observes Firebase auth state and pushes IS_SIGNED_IN + NEEDS_MIC_PERMISSION on every
     * auth change. Fires immediately on startup with the current state.
     */
    private fun observeAuthForWidget() {
        appScope.launch {
            callbackFlow<Boolean> {
                val listener = FirebaseAuth.AuthStateListener { auth ->
                    trySend(auth.currentUser != null)
                }
                FirebaseAuth.getInstance().addAuthStateListener(listener)
                awaitClose { FirebaseAuth.getInstance().removeAuthStateListener(listener) }
            }.collect { isSignedIn ->
                pushWidgetState(isSignedIn)
            }
        }
    }

    /**
     * Refreshes widget state every time any activity resumes (i.e. whenever the app comes
     * to the foreground). This ensures the widget instantly reflects:
     *  - mic permission granted/revoked via the system dialog
     *  - any other permission change made in system Settings
     *
     * onActivityResumed is the right hook because it fires the moment the activity window
     * is interactive again — which is exactly when the permission state is finalised after
     * the system permission dialog is dismissed.
     */
    private fun observeAppForegroundForWidget() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                appScope.launch {
                    pushWidgetState(FirebaseAuth.getInstance().currentUser != null)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private suspend fun pushWidgetState(isSignedIn: Boolean) {
        try {
            val needsMicPermission = isSignedIn &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            val manager = GlanceAppWidgetManager(this@VoiceMindApp)
            val ids = manager.getGlanceIds(RecordingWidget::class.java)
            ids.forEach { id ->
                updateAppWidgetState(this@VoiceMindApp, id) { prefs ->
                    prefs[RecordingWidgetStateKeys.IS_SIGNED_IN] = isSignedIn
                    prefs[RecordingWidgetStateKeys.NEEDS_MIC_PERMISSION] = needsMicPermission
                }
                RecordingWidget().update(this@VoiceMindApp, id)
            }
        } catch (_: Exception) { }
    }
}
