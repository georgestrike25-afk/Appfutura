package com.cumbre.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.cumbre.core.geo.GeoBounds
import com.cumbre.core.geo.GeoPoint

@Entity(
    tableName = "routes",
    indices = [Index(value = ["fingerprint"], unique = true), Index("favorite"), Index("lastOpenedAt")],
)
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Nombre local, editable por el usuario. */
    val name: String,
    /** Nombre que traía el archivo GPX. */
    val originalName: String?,
    val description: String?,
    /** [com.cumbre.core.library.RouteSource]. */
    val source: String,
    val sourceUrl: String?,
    val originalFileName: String?,
    /** Ruta relativa a filesDir del GPX original conservado. */
    val filePath: String,
    val fileSizeBytes: Long,
    val fingerprint: String,
    val distanceMeters: Double,
    val elevationGainMeters: Double?,
    val elevationLossMeters: Double?,
    val minElevationMeters: Double?,
    val maxElevationMeters: Double?,
    val estimatedDurationSeconds: Long?,
    val recordedDurationSeconds: Long?,
    val pointCount: Int,
    val segmentCount: Int,
    val waypointCount: Int,
    val minLat: Double?,
    val minLon: Double?,
    val maxLat: Double?,
    val maxLon: Double?,
    val startLat: Double?,
    val startLon: Double?,
    /** Polilínea codificada simplificada para miniaturas. */
    val thumbnail: String,
    /** Perfil de elevación reducido ([com.cumbre.core.analysis.AnalysisCodec]). */
    val profile: String,
    /** Avisos de calidad codificados. */
    val issues: String,
    val favorite: Boolean = false,
    val createdAt: Long,
    val lastOpenedAt: Long? = null,
) {
    val bounds: GeoBounds?
        get() = if (minLat != null && minLon != null && maxLat != null && maxLon != null) {
            GeoBounds(minLat, minLon, maxLat, maxLon)
        } else {
            null
        }

    val start: GeoPoint? get() = if (startLat != null && startLon != null) GeoPoint(startLat, startLon) else null
}

@Entity(
    tableName = "waypoints",
    foreignKeys = [ForeignKey(entity = RouteEntity::class, parentColumns = ["id"], childColumns = ["routeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routeId")],
)
data class WaypointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val position: Int,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val name: String?,
    val description: String?,
    val symbol: String?,
)

@Entity(tableName = "activities", indices = [Index("state"), Index("startedAt")])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** [com.cumbre.core.recording.StoredActivityState]. */
    val state: String,
    /** Ruta seguida durante la actividad, si la hubo. */
    val routeId: Long?,
    val routeName: String?,
    val startedAt: Long,
    val endedAt: Long? = null,
    /** Reloj persistido ([com.cumbre.core.recording.ActivityClock]). */
    val accumulatedMillis: Long = 0,
    val runningSince: Long? = null,
    val currentSegment: Int = 0,
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val elevationLossMeters: Double = 0.0,
    val movingMillis: Long = 0,
    val maxSpeedMps: Double = 0.0,
    val pointCount: Int = 0,
    val lastPointTime: Long? = null,
    /** Ruta creada a partir de esta actividad («Guardar como ruta»). */
    val savedRouteId: Long? = null,
)

@Entity(
    tableName = "activity_points",
    foreignKeys = [ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("activityId")],
)
data class ActivityPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val segment: Int,
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val altitude: Double?,
    val accuracy: Double?,
    val speed: Double?,
)

@Entity(tableName = "offline_maps", indices = [Index(value = ["filePath"], unique = true)])
data class OfflineMapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val originalFileName: String,
    /** Ruta absoluta del archivo .map copiado al almacenamiento de la app. */
    val filePath: String,
    val sizeBytes: Long,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val fileVersion: Int,
    val dataTimestamp: Long,
    val createdBy: String?,
    val importedAt: Long,
    val enabled: Boolean = true,
    /** Problema detectado en la última verificación (archivo borrado, ilegible, etc.). */
    val problem: String? = null,
) {
    val bounds: GeoBounds get() = GeoBounds(minLat, minLon, maxLat, maxLon)
}
