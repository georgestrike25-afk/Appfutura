package com.cumbre.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.cumbre.app.data.db.ActivityDao
import com.cumbre.app.data.db.ActivityEntity
import com.cumbre.app.data.db.ActivityPointEntity
import com.cumbre.core.gpx.GpxDocument
import com.cumbre.core.gpx.GpxWriter
import com.cumbre.core.recording.RecordedPoint
import com.cumbre.core.recording.RecordingExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class ActivityRepository(
    private val context: Context,
    private val dao: ActivityDao,
    private val routes: RouteRepository,
    private val files: FileStore,
) {
    fun observeFinished(): Flow<List<ActivityEntity>> = dao.observeFinished()
    fun observe(id: Long): Flow<ActivityEntity?> = dao.observe(id)
    suspend fun get(id: Long): ActivityEntity? = dao.get(id)

    suspend fun points(id: Long): List<RecordedPoint> = withContext(Dispatchers.IO) { dao.points(id).map { it.toRecorded() } }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim().ifBlank { "Actividad" })
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun document(activity: ActivityEntity): GpxDocument =
        RecordingExport.toGpx(
            name = activity.name,
            startedAtMillis = activity.startedAt,
            points = points(activity.id),
            description = activity.routeName?.let { "Siguiendo: $it" },
        )

    suspend fun exportTo(activity: ActivityEntity, target: Uri): Boolean = withContext(Dispatchers.IO) {
        val doc = document(activity)
        runCatching {
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                out.bufferedWriter().let { GpxWriter.write(doc, it) }
            } ?: return@withContext false
        }.isSuccess
    }

    suspend fun shareUri(activity: ActivityEntity): Uri = withContext(Dispatchers.IO) {
        val file = File(files.exportsDir, files.safeFileName(activity.name, "gpx"))
        file.bufferedWriter().use { GpxWriter.write(document(activity), it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Guarda la actividad como ruta propia en la biblioteca («Mis rutas»). */
    suspend fun saveAsRoute(activity: ActivityEntity): Long? {
        val doc = document(activity)
        if (doc.lines().flatten().size < 2) return null
        val routeId = routes.createFromDocument(doc, activity.name)
        dao.setSavedRoute(activity.id, routeId)
        return routeId
    }
}

fun ActivityPointEntity.toRecorded() = RecordedPoint(segment, latitude, longitude, timeMillis, altitude, accuracy, speed)
