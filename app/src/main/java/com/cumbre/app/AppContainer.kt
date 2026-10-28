package com.cumbre.app

import android.content.Context
import com.cumbre.app.data.ActivityRepository
import com.cumbre.app.data.DataPortability
import com.cumbre.app.data.FileStore
import com.cumbre.app.data.MapRepository
import com.cumbre.app.data.ReliefRepository
import com.cumbre.app.data.RouteRepository
import com.cumbre.app.data.SettingsRepository
import com.cumbre.app.data.db.CumbreDatabase
import com.cumbre.app.location.AndroidLocationSource
import com.cumbre.app.tracking.TrackingManager

/**
 * Inyección de dependencias manual: el grafo es chico y no justifica un framework.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: CumbreDatabase = CumbreDatabase.create(appContext)
    val files = FileStore(appContext)
    val settings = SettingsRepository(appContext)
    val routes = RouteRepository(appContext, database.routes(), database.activities(), files)
    val activities = ActivityRepository(appContext, database.activities(), routes, files)
    val maps = MapRepository(database.maps(), files)
    val relief = ReliefRepository(files)
    val locationSource = AndroidLocationSource(appContext)
    val trackingManager = TrackingManager(appContext, database.activities(), routes, settings)
    val incoming = IncomingContent()
    val dataPortability = DataPortability(
        appContext, database.routes(), database.activities(), routes, activities, maps, relief, settings, files,
    )
}
