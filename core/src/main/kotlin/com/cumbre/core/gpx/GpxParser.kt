package com.cumbre.core.gpx

import com.cumbre.core.geo.GeoPoint
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.ext.DefaultHandler2
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/**
 * Lector GPX 1.0/1.1 basado en SAX (streaming): no carga el árbol XML completo en memoria
 * y rechaza DOCTYPE y entidades externas para evitar ataques XXE o "billion laughs".
 */
class GpxParser(
    private val maxPoints: Int = DEFAULT_MAX_POINTS,
) {
    fun parse(input: InputStream): GpxParseResult {
        val buffered = BufferedInputStream(input)
        return try {
            buffered.mark(8)
            if (buffered.read() == -1) {
                return GpxParseResult.Failure(GpxErrorType.EMPTY_FILE, "El archivo está vacío.")
            }
            buffered.reset()
            val handler = Handler(maxPoints)
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }
            trySetFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
            trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            val reader = factory.newSAXParser().xmlReader
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.entityResolver = handler
            runCatching { reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler) }
            runCatching { reader.setProperty("http://xml.org/sax/properties/declaration-handler", handler) }
            reader.parse(InputSource(buffered))
            handler.result()
        } catch (e: GpxException) {
            GpxParseResult.Failure(e.type, e.message ?: "Archivo GPX no válido.")
        } catch (e: SAXParseException) {
            val unsafe = e.message?.contains("DOCTYPE", ignoreCase = true) == true
            if (unsafe) {
                GpxParseResult.Failure(GpxErrorType.UNSAFE_CONTENT, MSG_UNSAFE)
            } else {
                GpxParseResult.Failure(
                    GpxErrorType.MALFORMED_XML,
                    "El archivo no es un XML válido (línea ${e.lineNumber}). Puede estar dañado o incompleto.",
                )
            }
        } catch (e: SAXException) {
            val cause = e.exception
            if (cause is GpxException) {
                GpxParseResult.Failure(cause.type, cause.message ?: "Archivo GPX no válido.")
            } else {
                GpxParseResult.Failure(GpxErrorType.MALFORMED_XML, "El archivo no es un XML válido.")
            }
        } catch (e: IOException) {
            GpxParseResult.Failure(GpxErrorType.READ_ERROR, "No se pudo leer el archivo: ${e.message ?: "error de lectura"}.")
        } catch (e: OutOfMemoryError) {
            GpxParseResult.Failure(GpxErrorType.TOO_LARGE, "El archivo es demasiado grande para procesarlo en este dispositivo.")
        }
    }

    private fun trySetFeature(factory: SAXParserFactory, name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (_: Exception) {
            // Algunos analizadores (por ejemplo, Expat en Android) no reconocen todas las
            // características; el manejador igualmente rechaza DOCTYPE y entidades externas.
        }
    }

    private class Handler(private val maxPoints: Int) : DefaultHandler2() {
        private var sawRoot = false
        private var version: String? = null
        private var creator: String? = null

        private val waypoints = ArrayList<GpxWaypoint>()
        private val tracks = ArrayList<GpxTrack>()
        private val routes = ArrayList<GpxRoute>()
        private var metadata = GpxMetadata()

        private var invalidCoordinates = 0
        private var emptySegments = 0
        private var invalidElevations = 0
        private var invalidTimes = 0
        private var pointCount = 0

        private val path = ArrayList<String>()
        private val text = StringBuilder()

        // Estado del elemento en construcción
        private var currentTrack: TrackBuilder? = null
        private var currentSegment: ArrayList<GpxPoint>? = null
        private var currentRoute: RouteBuilder? = null
        private var currentPoint: PointBuilder? = null
        private var inMetadata = false
        private var metaName: String? = null
        private var metaDesc: String? = null
        private var metaAuthor: String? = null
        private var metaTime: Long? = null
        private var metaKeywords: String? = null
        private val metaLinks = ArrayList<GpxLink>()
        private var pendingLinkHref: String? = null
        private var pendingLinkText: String? = null

        fun result(): GpxParseResult {
            if (!sawRoot) return GpxParseResult.Failure(GpxErrorType.NOT_GPX, MSG_NOT_GPX)
            val doc = GpxDocument(
                version = version,
                creator = creator,
                metadata = metadata,
                waypoints = waypoints,
                tracks = tracks,
                routes = routes,
            )
            if (doc.lines().isEmpty() && waypoints.isEmpty()) {
                return GpxParseResult.Failure(
                    GpxErrorType.NO_GEOMETRY,
                    if (invalidCoordinates > 0) {
                        "El archivo no tiene puntos válidos: se descartaron $invalidCoordinates coordenadas fuera de rango."
                    } else {
                        "El archivo GPX no contiene tracks, rutas ni waypoints."
                    },
                )
            }
            return GpxParseResult.Success(
                doc,
                GpxParseReport(invalidCoordinates, emptySegments, invalidElevations, invalidTimes),
            )
        }

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException(GpxException(GpxErrorType.UNSAFE_CONTENT, MSG_UNSAFE))
        }

        override fun resolveEntity(name: String?, publicId: String?, baseURI: String?, systemId: String?): InputSource {
            throw SAXException(GpxException(GpxErrorType.UNSAFE_CONTENT, MSG_UNSAFE))
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            // Nunca se accede a recursos externos.
            return InputSource(StringReader(""))
        }

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
            val name = elementName(localName, qName)
            if (path.isEmpty()) {
                if (name != "gpx") throw SAXException(GpxException(GpxErrorType.NOT_GPX, MSG_NOT_GPX))
                sawRoot = true
                version = attributes.value("version")
                creator = attributes.value("creator")
            }
            path.add(name)
            text.setLength(0)
            val parent = path.getOrNull(path.size - 2)
            when (name) {
                "metadata" -> if (parent == "gpx") inMetadata = true
                "trk" -> if (parent == "gpx") currentTrack = TrackBuilder()
                "trkseg" -> if (currentTrack != null) currentSegment = ArrayList()
                "rte" -> if (parent == "gpx") currentRoute = RouteBuilder()
                "trkpt", "rtept", "wpt" -> {
                    val lat = attributes.value("lat")?.trim()?.toDoubleOrNull()
                    val lon = attributes.value("lon")?.trim()?.toDoubleOrNull()
                    currentPoint = PointBuilder(name, lat, lon)
                }
                "link" -> {
                    // GPX 1.1: <link href="...">; GPX 1.0 usa <url>.
                    pendingLinkHref = attributes.value("href")
                    pendingLinkText = null
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val name = elementName(localName, qName)
            val value = text.toString().trim()
            text.setLength(0)
            val parent = path.getOrNull(path.size - 2)
            val point = currentPoint
            when {
                point != null && name != point.kind -> applyPointField(point, name, value, parent)
                name == "trkpt" || name == "rtept" || name == "wpt" -> finishPoint(name, parent)
                name == "trkseg" -> {
                    val seg = currentSegment
                    if (seg != null) {
                        if (seg.isEmpty()) emptySegments++ else currentTrack?.segments?.add(seg)
                    }
                    currentSegment = null
                }
                name == "trk" && parent == "gpx" -> {
                    currentTrack?.let { tracks.add(it.build()) }
                    currentTrack = null
                }
                name == "rte" && parent == "gpx" -> {
                    currentRoute?.let { routes.add(it.build()) }
                    currentRoute = null
                }
                name == "metadata" && parent == "gpx" -> {
                    inMetadata = false
                    metadata = GpxMetadata(metaName, metaDesc, metaAuthor, metaLinks.toList(), metaTime, metaKeywords)
                }
                inMetadata -> applyMetadataField(name, value, parent)
                parent == "trk" && currentTrack != null -> when (name) {
                    "name" -> currentTrack?.name = value.ifEmpty { null }
                    "desc" -> currentTrack?.description = value.ifEmpty { null }
                    "type" -> currentTrack?.type = value.ifEmpty { null }
                }
                parent == "rte" && currentRoute != null -> when (name) {
                    "name" -> currentRoute?.name = value.ifEmpty { null }
                    "desc" -> currentRoute?.description = value.ifEmpty { null }
                }
                parent == "gpx" -> when (name) {
                    // GPX 1.0 guarda los metadatos directamente bajo <gpx>.
                    "name" -> if (metadata.name == null) metadata = metadata.copy(name = value.ifEmpty { null })
                    "desc" -> if (metadata.description == null) metadata = metadata.copy(description = value.ifEmpty { null })
                    "author" -> if (metadata.author == null) metadata = metadata.copy(author = value.ifEmpty { null })
                    "url" -> if (value.isNotEmpty()) metadata = metadata.copy(links = metadata.links + GpxLink(value))
                    "time" -> if (metadata.timeMillis == null) metadata = metadata.copy(timeMillis = GpxTime.parse(value))
                    "keywords" -> if (metadata.keywords == null) metadata = metadata.copy(keywords = value.ifEmpty { null })
                }
            }
            path.removeAt(path.lastIndex)
        }

        private fun applyMetadataField(name: String, value: String, parent: String?) {
            when (name) {
                "name" -> if (parent == "metadata") metaName = value.ifEmpty { null } else if (parent == "author") metaAuthor = value.ifEmpty { null }
                "desc" -> if (parent == "metadata") metaDesc = value.ifEmpty { null }
                "author" -> if (metaAuthor == null && value.isNotEmpty()) metaAuthor = value
                "time" -> if (parent == "metadata") metaTime = GpxTime.parse(value)
                "keywords" -> metaKeywords = value.ifEmpty { null }
                "text" -> if (parent == "link") pendingLinkText = value.ifEmpty { null }
                "link" -> {
                    val href = pendingLinkHref
                    if (parent == "metadata" && !href.isNullOrBlank()) metaLinks.add(GpxLink(href, pendingLinkText))
                    pendingLinkHref = null
                }
            }
        }

        private fun applyPointField(point: PointBuilder, name: String, value: String, parent: String?) {
            if (parent != point.kind) return
            when (name) {
                "ele" -> {
                    val ele = value.replace(',', '.').toDoubleOrNull()
                    if (ele != null && ele.isFinite() && ele in MIN_ELEVATION..MAX_ELEVATION) {
                        point.elevation = ele
                    } else if (value.isNotEmpty()) {
                        invalidElevations++
                    }
                }
                "time" -> {
                    val t = GpxTime.parse(value)
                    if (t != null) point.time = t else if (value.isNotEmpty()) invalidTimes++
                }
                "name" -> point.name = value.ifEmpty { null }
                "desc" -> point.description = value.ifEmpty { null }
                "cmt" -> if (point.description == null) point.description = value.ifEmpty { null }
                "sym" -> point.symbol = value.ifEmpty { null }
                "type" -> point.type = value.ifEmpty { null }
            }
        }

        private fun finishPoint(name: String, parent: String?) {
            val p = currentPoint ?: return
            currentPoint = null
            val lat = p.lat
            val lon = p.lon
            if (lat == null || lon == null || !GeoPoint.isValid(lat, lon)) {
                invalidCoordinates++
                return
            }
            pointCount++
            if (pointCount > maxPoints) {
                throw SAXException(
                    GpxException(
                        GpxErrorType.TOO_LARGE,
                        "El archivo tiene más de $maxPoints puntos y no puede procesarse en el teléfono.",
                    ),
                )
            }
            when (name) {
                "trkpt" -> if (parent == "trkseg") currentSegment?.add(GpxPoint(lat, lon, p.elevation, p.time))
                "rtept" -> if (parent == "rte") currentRoute?.points?.add(GpxPoint(lat, lon, p.elevation, p.time))
                "wpt" -> if (parent == "gpx") {
                    waypoints.add(GpxWaypoint(lat, lon, p.elevation, p.time, p.name, p.description, p.symbol, p.type))
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (text.length < MAX_TEXT_LENGTH) text.appendRange(ch, start, start + length)
        }

        override fun fatalError(e: SAXParseException) = throw e
        override fun error(e: SAXParseException) {}
        override fun warning(e: SAXParseException) {}

        private fun elementName(localName: String?, qName: String?): String {
            if (!localName.isNullOrEmpty()) return localName
            val q = qName.orEmpty()
            return q.substringAfter(':')
        }

        private fun Attributes.value(name: String): String? {
            for (i in 0 until length) {
                val ln = getLocalName(i).takeUnless { it.isNullOrEmpty() } ?: getQName(i).substringAfter(':')
                if (ln == name) return getValue(i)
            }
            return null
        }
    }

    private class TrackBuilder {
        var name: String? = null
        var description: String? = null
        var type: String? = null
        val segments = ArrayList<List<GpxPoint>>()
        fun build() = GpxTrack(name, description, type, segments.toList())
    }

    private class RouteBuilder {
        var name: String? = null
        var description: String? = null
        val points = ArrayList<GpxPoint>()
        fun build() = GpxRoute(name, description, points.toList())
    }

    private class PointBuilder(val kind: String, val lat: Double?, val lon: Double?) {
        var elevation: Double? = null
        var time: Long? = null
        var name: String? = null
        var description: String? = null
        var symbol: String? = null
        var type: String? = null
    }

    companion object {
        const val DEFAULT_MAX_POINTS = 1_000_000
        private const val MAX_TEXT_LENGTH = 64 * 1024
        const val MIN_ELEVATION = -500.0
        const val MAX_ELEVATION = 9_000.0
        private const val MSG_NOT_GPX = "El archivo no es un GPX: falta el elemento <gpx>."
        private const val MSG_UNSAFE =
            "El archivo contiene declaraciones DOCTYPE o entidades externas, que no se admiten por seguridad."
    }
}
