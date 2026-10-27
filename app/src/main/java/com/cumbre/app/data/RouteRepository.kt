package com.cumbre.app.data

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import com.cumbre.app.data.db.ActivityDao
import com.cumbre.app.data.db.RouteDao
import com.cumbre.app.data.db.RouteEntity
import com.cumbre.app.data.db.WaypointEntity
import com.cumbre.core.analysis.AnalysisCodec
import com.cumbre.core.analysis.RouteAnalysis
import com.cumbre.core.analysis.RouteAnalyzer
import com.cumbre.core.analysis.RouteFingerprint
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.geo.PolylineCodec
import com.cumbre.core.geo.Simplifier
import com.cumbre.core.gpx.GpxDocument
import com.cumbre.core.gpx.GpxLink
import com.cumbre.core.gpx.GpxParseReport
import com.cumbre.core.gpx.GpxParseResult
import com.cumbre.core.gpx.GpxParser
import com.cumbre.core.gpx.GpxWaypoint
import com.cumbre.core.gpx.GpxWriter
import com.cumbre.core.library.RouteSource
import com.cumbre.core.library.WikilocLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Geometría completa de una ruta guardada, cargada desde su GPX. */
data class RouteGeometry(
    val document: GpxDocument,
    val lines: List<List<GeoPoint>>,
    val waypoints: List<GpxWaypoint>,
)

/** Resultado de analizar un archivo antes de guardarlo. */
data class PendingImport(
    val tempFile: File,
    val document: GpxDocument,
    val report: GpxParseReport,
    val analysis: RouteAnalysis,
    val fingerprint: String,
    val duplicateOf: RouteEntity?,
    val suggestedName: String,
    val detectedSourceUrl: String?,
    val originalFileName: String?,
    val fileSizeBytes: Long,
)

sealed interface PrepareResult {
    data class Ready(val pending: PendingImport) : PrepareResult
    data class Failed(val message: String) : PrepareResult
}

class RouteRepository(
    private val context: Context,
    private val routeDao: RouteDao,
    private val activityDao: ActivityDao,
    private val files: FileStore,
    private val parser: GpxParser = GpxParser(),
) {
    private val geometryCache = LruCache<Long, RouteGeometry>(4)

    fun observeRoutes(): Flow<List<RouteEntity>> = routeDao.observeAll()
    fun observeRoute(id: Long): Flow<RouteEntity?> = routeDao.observe(id)
    fun observeMostRecent(): Flow<RouteEntity?> = routeDao.observeMostRecent()
    suspend fun getRoute(id: Long): RouteEntity? = routeDao.get(id)

    /** Copia el archivo a un área temporal, lo valida y lo analiza sin bloquear la interfaz. */
    suspend fun prepareImport(uri: Uri): PrepareResult = withContext(Dispatchers.IO) {
        val declared = files.declaredSize(uri)
        if (declared != null && declared > MAX_GPX_BYTES) {
            return@withContext PrepareResult.Failed(tooLargeMessage())
        }
        val displayName = files.displayName(uri)
        val temp = File(files.importsDir, "import-${System.nanoTime()}.gpx")
        val size = try {
            files.copyFromUri(uri, temp, MAX_GPX_BYTES)
        } catch (e: FileTooLargeException) {
            return@withContext PrepareResult.Failed(tooLargeMessage())
        } catch (e: SecurityException) {
            return@withContext PrepareResult.Failed("Cumbre no tiene permiso para leer ese archivo. Probá abrirlo desde el selector de archivos.")
        } catch (e: IOException) {
            return@withContext PrepareResult.Failed("No se pudo leer el archivo: ${e.message ?: "error de lectura"}.")
        }
        prepareFromFile(temp, displayName, size)
    }

    suspend fun prepareFromFile(temp: File, displayName: String?, size: Long): PrepareResult = withContext(Dispatchers.Default) {
        val parsed = temp.inputStream().use { parser.parse(it) }
        when (parsed) {
            is GpxParseResult.Failure -> {
                temp.delete()
                PrepareResult.Failed(parsed.message)
            }
            is GpxParseResult.Success -> {
                val doc = parsed.document
                if (doc.lines().isEmpty()) {
                    temp.delete()
                    return@withContext PrepareResult.Failed(
                        "El archivo solo tiene ${doc.waypoints.size} waypoints y ningún track o ruta para seguir.",
                    )
                }
                val analysis = RouteAnalyzer.analyze(doc, parsed.report)
                val fingerprint = RouteFingerprint.of(doc)
                val duplicate = routeDao.findByFingerprint(fingerprint)
                val url = WikilocLinks.fromLinks(doc.metadata.links.map { it.href })
                PrepareResult.Ready(
                    PendingImport(
                        tempFile = temp,
                        document = doc,
                        report = parsed.report,
                        analysis = analysis,
                        fingerprint = fingerprint,
                        duplicateOf = duplicate,
                        suggestedName = suggestName(doc, displayName),
                        detectedSourceUrl = url,
                        originalFileName = displayName,
                        fileSizeBytes = size,
                    ),
                )
            }
        }
    }

    /** Guarda la ruta analizada. Devuelve el id de la ruta (la existente si era un duplicado). */
    suspend fun confirmImport(pending: PendingImport, name: String, sourceUrl: String?): Long = withContext(Dispatchers.IO) {
        routeDao.findByFingerprint(pending.fingerprint)?.let { existing ->
            pending.tempFile.delete()
            if (existing.sourceUrl == null && sourceUrl != null) routeDao.update(existing.copy(sourceUrl = sourceUrl))
            return@withContext existing.id
        }
        val target = files.newRouteFile()
        if (!pending.tempFile.renameTo(target)) {
            pending.tempFile.copyTo(target, overwrite = true)
            pending.tempFile.delete()
        }
        val isWikiloc = WikilocLinks.isWikilocUrl(sourceUrl) ||
            pending.document.creator?.contains("wikiloc", ignoreCase = true) == true
        val source = if (isWikiloc) RouteSource.WIKILOC else RouteSource.IMPORTED
        try {
            insertRoute(pending.document, pending.analysis, pending.fingerprint, target, name, source, sourceUrl, pending.originalFileName)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private suspend fun insertRoute(
        doc: GpxDocument,
        analysis: RouteAnalysis,
        fingerprint: String,
        file: File,
        name: String,
        source: RouteSource,
        sourceUrl: String?,
        originalFileName: String?,
    ): Long {
        val s = analysis.stats
        val thumbPoints = Simplifier.simplifyToMax(doc.lines().flatten().map { it.geo }, THUMB_POINTS)
        val entity = RouteEntity(
            name = name.trim().ifBlank { "Ruta sin nombre" },
            originalName = doc.displayName(),
            description = doc.displayDescription(),
            source = source.name,
            sourceUrl = sourceUrl?.takeIf { it.isNotBlank() },
            originalFileName = originalFileName,
            filePath = files.relativize(file),
            fileSizeBytes = file.length(),
            fingerprint = fingerprint,
            distanceMeters = s.distanceMeters,
            elevationGainMeters = s.elevationGainMeters,
            elevationLossMeters = s.elevationLossMeters,
            minElevationMeters = s.minElevationMeters,
            maxElevationMeters = s.maxElevationMeters,
            estimatedDurationSeconds = s.estimatedDurationSeconds,
            recordedDurationSeconds = s.recordedDurationSeconds,
            pointCount = s.pointCount,
            segmentCount = s.segmentCount,
            waypointCount = s.waypointCount,
            minLat = s.bounds?.minLatitude,
            minLon = s.bounds?.minLongitude,
            maxLat = s.bounds?.maxLatitude,
            maxLon = s.bounds?.maxLongitude,
            startLat = s.start?.latitude,
            startLon = s.start?.longitude,
            thumbnail = PolylineCodec.encode(thumbPoints),
            profile = AnalysisCodec.encodeProfile(analysis.profile),
            issues = AnalysisCodec.encodeIssues(analysis.issues),
            createdAt = System.currentTimeMillis(),
        )
        val waypoints = doc.waypoints.mapIndexed { i, w ->
            WaypointEntity(routeId = 0, position = i, latitude = w.latitude, longitude = w.longitude, elevation = w.elevation, name = w.name, description = w.description, symbol = w.symbol)
        }
        return routeDao.insertWithWaypoints(entity, waypoints)
    }

    /** Crea una ruta propia a partir de un GPX generado por Cumbre (actividad grabada). */
    suspend fun createFromDocument(doc: GpxDocument, name: String): Long = withContext(Dispatchers.IO) {
        val fingerprint = RouteFingerprint.of(doc)
        routeDao.findByFingerprint(fingerprint)?.let { return@withContext it.id }
        val file = files.newRouteFile()
        file.bufferedWriter().use { GpxWriter.write(doc, it) }
        val analysis = RouteAnalyzer.analyze(doc)
        try {
            insertRoute(doc, analysis, fingerprint, file, name, RouteSource.RECORDED, null, null)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    suspend fun discardPending(pending: PendingImport) = withContext(Dispatchers.IO) { pending.tempFile.delete() }

    suspend fun loadGeometry(route: RouteEntity): RouteGeometry? = withContext(Dispatchers.IO) {
        geometryCache.get(route.id)?.let { return@withContext it }
        val file = files.resolve(route.filePath)
        if (!file.exists()) return@withContext null
        val parsed = file.inputStream().use { parser.parse(it) }
        val doc = (parsed as? GpxParseResult.Success)?.document ?: return@withContext null
        val geometry = RouteGeometry(doc, doc.lines().map { line -> line.map { it.geo } }, doc.waypoints)
        geometryCache.put(route.id, geometry)
        geometry
    }

    suspend fun rename(id: Long, name: String) = routeDao.rename(id, name.trim().ifBlank { "Ruta sin nombre" })
    suspend fun setFavorite(id: Long, favorite: Boolean) = routeDao.setFavorite(id, favorite)
    suspend fun markOpened(id: Long) = routeDao.markOpened(id, System.currentTimeMillis())
    suspend fun setSourceUrl(route: RouteEntity, url: String?) = routeDao.update(route.copy(sourceUrl = url?.takeIf { it.isNotBlank() }))

    suspend fun delete(route: RouteEntity) = withContext(Dispatchers.IO) {
        geometryCache.remove(route.id)
        activityDao.clearSavedRoute(route.id)
        routeDao.delete(route.id)
        files.resolve(route.filePath).delete()
    }

    /** Documento a exportar: el GPX original con el nombre local y el enlace de origen. */
    private suspend fun exportDocument(route: RouteEntity): GpxDocument? {
        val geometry = loadGeometry(route) ?: return null
        val doc = geometry.document
        val links = buildList {
            addAll(doc.metadata.links)
            route.sourceUrl?.let { url -> if (none { it.href == url }) add(GpxLink(url, "Origen")) }
        }
        return doc.copy(metadata = doc.metadata.copy(name = route.name, links = links))
    }

    suspend fun exportTo(route: RouteEntity, target: Uri): Boolean = withContext(Dispatchers.IO) {
        val doc = exportDocument(route) ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                out.bufferedWriter().let { w -> GpxWriter.write(doc, w) }
            } ?: return@withContext false
        }.isSuccess
    }

    /** Prepara una copia para compartir mediante FileProvider. */
    suspend fun shareUri(route: RouteEntity): Uri? = withContext(Dispatchers.IO) {
        val doc = exportDocument(route) ?: return@withContext null
        val file = File(files.exportsDir, files.safeFileName(route.name, "gpx"))
        file.bufferedWriter().use { GpxWriter.write(doc, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun exportDocumentForBackup(route: RouteEntity): GpxDocument? = exportDocument(route)

    private fun suggestName(doc: GpxDocument, fileName: String?): String =
        doc.displayName()
            ?: fileName?.substringBeforeLast('.')?.replace('_', ' ')?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Ruta del ${SimpleDateFormat("d 'de' MMMM", Locale.forLanguageTag("es-AR")).format(Date())}"

    private fun tooLargeMessage() =
        "El archivo supera los ${MAX_GPX_BYTES / (1024 * 1024)} MB permitidos para un GPX."

    companion object {
        const val MAX_GPX_BYTES = 50L * 1024 * 1024
        const val THUMB_POINTS = 80
    }
}
