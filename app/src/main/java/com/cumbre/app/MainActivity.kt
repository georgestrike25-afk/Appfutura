package com.cumbre.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cumbre.app.data.Settings
import com.cumbre.app.ui.CumbreRoot
import com.cumbre.app.ui.theme.CumbreTheme
import com.cumbre.core.library.WikilocLinks

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as CumbreApp).container }
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !settingsLoaded }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings = container.settings.settings.collectAsStateWithLifecycle(initialValue = null).value
            if (settings != null) settingsLoaded = true
            CumbreTheme(themeMode = (settings ?: Settings()).themeMode) {
                if (settings != null) CumbreRoot(container = container, settings = settings)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_TRACKING, false)) {
            container.incoming.offer(Incoming.OpenTracking)
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { container.incoming.offer(Incoming.File(it)) }
            Intent.ACTION_SEND -> {
                val stream = intent.streamUri()
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> container.incoming.offer(Incoming.File(stream))
                    WikilocLinks.extractUrl(text) != null ->
                        container.incoming.offer(Incoming.WikilocLink(WikilocLinks.extractUrl(text)!!))
                }
            }
        }
    }

    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    companion object {
        const val EXTRA_OPEN_TRACKING = "com.cumbre.app.OPEN_TRACKING"
    }
}
