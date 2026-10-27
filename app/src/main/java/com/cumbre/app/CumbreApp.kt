package com.cumbre.app

import android.app.Application
import com.cumbre.app.tracking.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class CumbreApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Mapsforge necesita inicializar su fábrica gráfica una sola vez por proceso.
        AndroidGraphicFactory.createInstance(this)
        container = AppContainer(this)
        TrackingService.createChannels(this)
        appScope.launch {
            container.files.clearCacheDirs()
            container.maps.verifyAll()
        }
    }
}
