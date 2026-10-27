package com.cumbre.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** Gestión de los archivos que Cumbre guarda en su almacenamiento privado. */
class FileStore(private val context: Context) {
    val routesDir: File get() = File(context.filesDir, "routes").apply { mkdirs() }
    val importsDir: File get() = File(context.cacheDir, "imports").apply { mkdirs() }
    val exportsDir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    /** Los mapas pueden pesar cientos de MB: se guardan en el almacenamiento externo de la app si existe. */
    val mapsDir: File
        get() = (context.getExternalFilesDir("maps") ?: File(context.filesDir, "maps")).apply { mkdirs() }

    fun resolve(relativePath: String): File = File(context.filesDir, relativePath)

    fun relativize(file: File): String = file.relativeTo(context.filesDir).path

    fun newRouteFile(): File = File(routesDir, "${UUID.randomUUID()}.gpx")

    fun displayName(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    fun declaredSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    /**
     * Copia el contenido de [uri] a [target] sin superar [maxBytes].
     * Lanza [FileTooLargeException] si el archivo es más grande.
     */
    fun copyFromUri(uri: Uri, target: File, maxBytes: Long, onProgress: ((Long) -> Unit)? = null): Long {
        val input: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("No se pudo abrir el archivo seleccionado.")
        var total = 0L
        input.use { inp ->
            target.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var lastReport = 0L
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) {
                        out.close()
                        target.delete()
                        throw FileTooLargeException(maxBytes)
                    }
                    out.write(buf, 0, n)
                    if (onProgress != null && total - lastReport > 1024 * 1024) {
                        lastReport = total
                        onProgress(total)
                    }
                }
            }
        }
        return total
    }

    fun freeBytes(dir: File): Long = runCatching { StatFs(dir.absolutePath).availableBytes }.getOrDefault(0L)

    fun clearCacheDirs() {
        importsDir.listFiles()?.forEach { it.delete() }
        exportsDir.listFiles()?.forEach { it.delete() }
    }

    /** Nombre de archivo seguro para exportar. */
    fun safeFileName(name: String, extension: String): String {
        val base = name.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(80).ifBlank { "cumbre" }
        return "$base.$extension"
    }
}

class FileTooLargeException(val maxBytes: Long) : IOException("El archivo supera el tamaño máximo permitido.")
