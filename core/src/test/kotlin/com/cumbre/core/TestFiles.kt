package com.cumbre.core

import com.cumbre.core.gpx.GpxParseResult
import com.cumbre.core.gpx.GpxParser
import java.io.InputStream

object TestFiles {
    fun open(name: String): InputStream =
        requireNotNull(javaClass.classLoader.getResourceAsStream("gpx/$name")) { "Falta el recurso $name" }

    fun parse(name: String): GpxParseResult = open(name).use { GpxParser().parse(it) }

    fun parseOk(name: String): GpxParseResult.Success {
        val r = parse(name)
        check(r is GpxParseResult.Success) { "Se esperaba éxito al leer $name y se obtuvo $r" }
        return r
    }
}
