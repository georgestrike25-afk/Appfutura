package com.cumbre.app.data

import android.content.Context
import android.net.Uri
import com.cumbre.app.data.db.ActivityDao
import com.cumbre.app.data.db.RouteDao
import com.cumbre.core.gpx.GpxWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exportación y eliminación de los datos personales guardados en el teléfono. */
class DataPortability(
    private val context: Context,
    private val routeDao: RouteDao,
    private val activityDao: ActivityDao,
    private val routes: RouteRepository,
    private val activities: ActivityRepository,
    private val maps: MapRepository,
    private val relief: ReliefRepository,
    private val settings: SettingsRepository,
    private val files: FileStore,
) {
    /** Escribe un ZIP con todas las rutas y actividades en formato GPX. Devuelve la cantidad de archivos. */
    suspend fun exportAll(target: Uri): Int = withContext(Dispatchers.IO) {
        var count = 0
        val used = HashSet<String>()
        fun unique(folder: String, name: String): String {
            var candidate = "$folder/${files.safeFileName(name, "gpx")}"
            var i = 2
            while (!used.add(candidate)) candidate = "$folder/${files.safeFileName("$name ($i)", "gpx")}".also { i++ }
            return candidate
        }
        val out = context.contentResolver.openOutputStream(target, "wt") ?: error("No se pudo crear el archivo de exportación.")
        ZipOutputStream(out.buffered()).use { zip ->
            val writer = zip.bufferedWriter()
            for (route in routeDao.getAll()) {
                val doc = routes.exportDocumentForBackup(route) ?: continue
                zip.putNextEntry(ZipEntry(unique("rutas", route.name)))
                GpxWriter.write(doc, writer)
                writer.flush()
                zip.closeEntry()
                count++
            }
            for (activity in activityDao.getAll()) {
                if (activity.pointCount == 0 && activities.points(activity.id).isEmpty()) continue
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(activity.startedAt))
                zip.putNextEntry(ZipEntry(unique("actividades", "$date ${activity.name}")))
                GpxWriter.write(activities.document(activity), writer)
                writer.flush()
                zip.closeEntry()
                count++
            }
            zip.putNextEntry(ZipEntry("LEEME.txt"))
            writer.write(
                "Exportación de Cumbre (${SimpleDateFormat("d/M/yyyy HH:mm", Locale.ROOT).format(Date())}).\n" +
                    "Contiene tus rutas y actividades en formato GPX 1.1.\n",
            )
            writer.flush()
            zip.closeEntry()
        }
        count
    }

    /** Borra rutas, actividades y preferencias. Los mapas se borran solo si se indica. */
    suspend fun deleteAll(includeMaps: Boolean) = withContext(Dispatchers.IO) {
        activityDao.deleteAll()
        routeDao.deleteAll()
        files.routesDir.listFiles()?.forEach { it.delete() }
        files.clearCacheDirs()
        if (includeMaps) {
            maps.deleteAll()
            relief.deleteAll()
        }
        settings.resetAll()
    }
}
