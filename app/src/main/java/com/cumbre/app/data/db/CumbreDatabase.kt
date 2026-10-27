package com.cumbre.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [RouteEntity::class, WaypointEntity::class, ActivityEntity::class, ActivityPointEntity::class, OfflineMapEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class CumbreDatabase : RoomDatabase() {
    abstract fun routes(): RouteDao
    abstract fun activities(): ActivityDao
    abstract fun maps(): OfflineMapDao

    companion object {
        const val NAME = "cumbre.db"

        fun create(context: Context): CumbreDatabase =
            Room.databaseBuilder(context.applicationContext, CumbreDatabase::class.java, NAME)
                // Versión inicial del esquema: las próximas versiones deben agregar migraciones
                // explícitas (Migration) para no perder rutas ni actividades.
                .build()
    }
}
