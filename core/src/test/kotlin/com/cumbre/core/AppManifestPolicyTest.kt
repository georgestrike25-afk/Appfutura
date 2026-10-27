package com.cumbre.core

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifica la política de permisos y de funcionamiento sin conexión declarada en el manifiesto
 * de la app Android (se ejecuta junto con las pruebas de `core`).
 */
class AppManifestPolicyTest {
    private val manifest: File = listOf(File("../app/src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }

    private val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun permissions(): Set<String> {
        val nodes = doc.getElementsByTagName("uses-permission")
        return (0 until nodes.length).map { (nodes.item(it) as org.w3c.dom.Element).getAttributeNS(androidNs, "name") }.toSet()
    }

    @Test
    fun `no declara acceso a Internet - la app funciona sin conexion`() {
        assertFalse("android.permission.INTERNET" in permissions())
        assertFalse("android.permission.ACCESS_NETWORK_STATE" in permissions())
    }

    @Test
    fun `solo pide los permisos necesarios y no ubicacion en segundo plano permanente`() {
        assertEquals(
            setOf(
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_LOCATION",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.VIBRATE",
            ),
            permissions(),
        )
        assertFalse("android.permission.ACCESS_BACKGROUND_LOCATION" in permissions())
    }

    @Test
    fun `el servicio de grabacion es de tipo location y no esta exportado`() {
        val services = doc.getElementsByTagName("service")
        val s = (0 until services.length).map { services.item(it) as org.w3c.dom.Element }.single { it.getAttributeNS(androidNs, "name").endsWith("TrackingService") }
        assertEquals("location", s.getAttributeNS(androidNs, "foregroundServiceType"))
        assertEquals("false", s.getAttributeNS(androidNs, "exported"))
    }

    @Test
    fun `acepta archivos GPX desde otras apps`() {
        val text = manifest.readText()
        assertTrue(text.contains("application/gpx+xml"))
        assertTrue(text.contains("android.intent.action.VIEW"))
        assertTrue(text.contains("android.intent.action.SEND"))
        assertTrue(text.contains(".*\\\\.gpx"))
    }
}
