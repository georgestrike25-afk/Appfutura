package com.cumbre.core.gpx

import java.io.Writer
import java.util.Locale

/** Genera GPX 1.1 válido. */
object GpxWriter {
    private const val CREATOR = "Cumbre"

    fun write(document: GpxDocument, out: Writer) {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.write(
            "<gpx version=\"1.1\" creator=\"$CREATOR\" xmlns=\"http://www.topografix.com/GPX/1/1\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n",
        )
        val m = document.metadata
        if (m.name != null || m.description != null || m.author != null || m.links.isNotEmpty() || m.timeMillis != null || m.keywords != null) {
            out.write("  <metadata>\n")
            m.name?.let { out.write("    <name>${escape(it)}</name>\n") }
            m.description?.let { out.write("    <desc>${escape(it)}</desc>\n") }
            m.author?.let { out.write("    <author><name>${escape(it)}</name></author>\n") }
            for (link in m.links) {
                out.write("    <link href=\"${escape(link.href)}\">")
                link.text?.let { out.write("<text>${escape(it)}</text>") }
                out.write("</link>\n")
            }
            m.timeMillis?.let { out.write("    <time>${GpxTime.format(it)}</time>\n") }
            m.keywords?.let { out.write("    <keywords>${escape(it)}</keywords>\n") }
            out.write("  </metadata>\n")
        }
        for (w in document.waypoints) {
            out.write("  <wpt lat=\"${coord(w.latitude)}\" lon=\"${coord(w.longitude)}\">\n")
            w.elevation?.let { out.write("    <ele>${ele(it)}</ele>\n") }
            w.timeMillis?.let { out.write("    <time>${GpxTime.format(it)}</time>\n") }
            w.name?.let { out.write("    <name>${escape(it)}</name>\n") }
            w.description?.let { out.write("    <desc>${escape(it)}</desc>\n") }
            w.symbol?.let { out.write("    <sym>${escape(it)}</sym>\n") }
            w.type?.let { out.write("    <type>${escape(it)}</type>\n") }
            out.write("  </wpt>\n")
        }
        for (r in document.routes) {
            out.write("  <rte>\n")
            r.name?.let { out.write("    <name>${escape(it)}</name>\n") }
            r.description?.let { out.write("    <desc>${escape(it)}</desc>\n") }
            for (p in r.points) writePoint(out, "rtept", p, "    ")
            out.write("  </rte>\n")
        }
        for (t in document.tracks) {
            out.write("  <trk>\n")
            t.name?.let { out.write("    <name>${escape(it)}</name>\n") }
            t.description?.let { out.write("    <desc>${escape(it)}</desc>\n") }
            t.type?.let { out.write("    <type>${escape(it)}</type>\n") }
            for (seg in t.segments) {
                out.write("    <trkseg>\n")
                for (p in seg) writePoint(out, "trkpt", p, "      ")
                out.write("    </trkseg>\n")
            }
            out.write("  </trk>\n")
        }
        out.write("</gpx>\n")
        out.flush()
    }

    fun writeToString(document: GpxDocument): String {
        val sw = java.io.StringWriter()
        write(document, sw)
        return sw.toString()
    }

    private fun writePoint(out: Writer, tag: String, p: GpxPoint, indent: String) {
        out.write("$indent<$tag lat=\"${coord(p.latitude)}\" lon=\"${coord(p.longitude)}\">")
        p.elevation?.let { out.write("<ele>${ele(it)}</ele>") }
        p.timeMillis?.let { out.write("<time>${GpxTime.format(it)}</time>") }
        out.write("</$tag>\n")
    }

    private fun coord(v: Double) = String.format(Locale.ROOT, "%.7f", v)
    private fun ele(v: Double) = String.format(Locale.ROOT, "%.1f", v)

    fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (c.code >= 0x20 || c == '\n' || c == '\t' || c == '\r') sb.append(c)
            }
        }
        return sb.toString()
    }
}
