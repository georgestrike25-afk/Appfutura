package com.cumbre.core.library

import java.net.URI

/**
 * Utilidades para enlaces públicos de Wikiloc. Cumbre no usa APIs privadas ni descarga
 * contenido automáticamente: solo reconoce y abre enlaces oficiales en el navegador.
 */
object WikilocLinks {
    const val HOME_URL = "https://www.wikiloc.com/"
    const val SEARCH_URL = "https://www.wikiloc.com/wikiloc/map.do"

    fun isWikilocUrl(text: String?): Boolean {
        val host = hostOf(text) ?: return false
        return host == "wikiloc.com" || host.endsWith(".wikiloc.com")
    }

    /** Busca un enlace de Wikiloc dentro de un texto compartido (por ejemplo, desde la app de Wikiloc). */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val regex = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.value.trimEnd('.', ',', ')', ';') }.firstOrNull { isWikilocUrl(it) }
    }

    /** Enlaces encontrados en los metadatos GPX que apunten a Wikiloc. */
    fun fromLinks(hrefs: List<String>): String? = hrefs.firstOrNull { isWikilocUrl(it) }

    private fun hostOf(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val uri = URI(text.trim())
            if (uri.scheme?.lowercase() !in setOf("http", "https")) null else uri.host?.lowercase()
        }.getOrNull()
    }
}
