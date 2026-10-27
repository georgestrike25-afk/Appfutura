package com.cumbre.core.gpx

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal object GpxTime {
    fun parse(value: String): Long? {
        val text = value.trim()
        if (text.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            // Algunos GPS omiten la zona horaria: se asume UTC, como indica el estándar GPX.
            ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    fun format(epochMillis: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epochMillis))
}
