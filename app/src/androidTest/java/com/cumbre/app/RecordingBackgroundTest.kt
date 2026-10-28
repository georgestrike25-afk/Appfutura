package com.cumbre.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cumbre.app.tracking.TrackingStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Grabación real en el emulador con la pantalla apagada y en modo Doze, tanto libre como
 * siguiendo una ruta. Requiere que el workflow `emulador.yml` esté enviando posiciones con
 * `adb emu geo fix` (script `scripts/emulador-gps.py`) y que los permisos de ubicación estén
 * concedidos.
 *
 * Limitación: el emulador no reproduce las capas de ahorro de batería propias de cada
 * fabricante (Xiaomi, Samsung…), que solo se pueden comprobar en el teléfono.
 */
@RunWith(AndroidJUnit4::class)
class RecordingBackgroundTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as CumbreApp
    private val container get() = app.container
    private val manager get() = container.trackingManager

    @Before
    fun setUp() = runBlocking {
        if (manager.state.value.isActive) manager.discard()
        instrumentation.shell("input keyevent KEYCODE_WAKEUP")
    }

    @After
    fun tearDown() = runBlocking {
        instrumentation.shell("dumpsys deviceidle unforce")
        instrumentation.shell("dumpsys battery reset")
        instrumentation.shell("input keyevent KEYCODE_WAKEUP")
        if (manager.state.value.isActive) manager.discard()
    }

    private fun serviceRunning(): Boolean =
        instrumentation.shell("dumpsys activity services com.cumbre.app").contains("TrackingService")

    private fun waitForPoints(min: Int, timeoutMillis: Long): Int {
        val end = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < end) {
            val n = manager.state.value.stats.pointCount
            if (n >= min) return n
            Thread.sleep(1_000)
        }
        return manager.state.value.stats.pointCount
    }

    private fun screenOffAndDoze() {
        instrumentation.shell("input keyevent KEYCODE_SLEEP")
        Thread.sleep(2_000)
        instrumentation.shell("dumpsys battery unplug")
        instrumentation.shell("dumpsys deviceidle force-idle")
    }

    @Test
    fun grabacionLibreSigueConPantallaApagadaYDoze() = runBlocking {
        manager.start(null)
        assertTrue("El servicio debe iniciar", serviceRunning())
        val first = waitForPoints(3, 60_000)
        assertTrue("Sin posiciones del emulador: ¿está corriendo emulador-gps.py? ($first)", first >= 3)

        screenOffAndDoze()
        val before = manager.state.value.stats.pointCount
        Thread.sleep(75_000)
        val after = manager.state.value.stats.pointCount
        println("CUMBRE_TEST libre: puntos antes=$before después=$after (75 s con pantalla apagada y Doze)")
        assertEquals(TrackingStatus.RECORDING, manager.state.value.status)
        assertTrue("El servicio debe seguir vivo con la pantalla apagada", serviceRunning())
        assertTrue("Debe seguir grabando puntos con la pantalla apagada ($before → $after)", after >= before + 15)

        // En pausa el servicio sigue vivo (sin GPS) y se puede reanudar.
        manager.pause()
        Thread.sleep(3_000)
        assertTrue("En pausa el servicio debe seguir vivo", serviceRunning())
        val paused = manager.state.value.stats.pointCount
        Thread.sleep(10_000)
        assertEquals("En pausa no se registran puntos", paused, manager.state.value.stats.pointCount)
        manager.resume()
        assertTrue(waitForPoints(paused + 5, 60_000) >= paused + 5)

        val id = manager.finish()
        assertNotNull(id)
        Thread.sleep(2_000)
        assertTrue("Al finalizar el servicio se detiene", !serviceRunning())
        val saved = container.activities.get(id!!)
        assertNotNull(saved)
        assertTrue("La actividad guardada conserva sus puntos", saved!!.pointCount >= after)
    }

    @Test
    fun seguirRutaSigueConPantallaApagadaYDoze() = runBlocking {
        val routeId = TestRoute.ensureImported(container, instrumentation.targetContext.cacheDir)
        manager.start(routeId)
        assertNotNull(manager.state.value.route)
        waitForPoints(3, 60_000)
        // Espera a ubicarse sobre el track.
        val end = System.currentTimeMillis() + 60_000
        while (manager.state.value.follow == null && System.currentTimeMillis() < end) Thread.sleep(1_000)
        val follow0 = manager.state.value.follow
        assertNotNull("Debe ubicarse sobre la ruta", follow0)

        screenOffAndDoze()
        val before = manager.state.value.stats.pointCount
        Thread.sleep(75_000)
        val s = manager.state.value
        println("CUMBRE_TEST ruta: puntos antes=$before después=${s.stats.pointCount}, falta ${s.follow?.remainingMeters} m")
        assertEquals(TrackingStatus.RECORDING, s.status)
        assertTrue(serviceRunning())
        assertTrue("Debe seguir grabando siguiendo la ruta (${before} → ${s.stats.pointCount})", s.stats.pointCount >= before + 15)
        assertNotNull("Debe seguir calculando lo que falta", s.follow)
        manager.discard()
    }

    @Test
    fun reinicioDelProcesoRestauraLaActividad() = runBlocking {
        manager.start(null)
        waitForPoints(3, 60_000)
        val id = manager.state.value.activityId
        // Simula que Android cerró el proceso y reinició el servicio: el estado en memoria se
        // pierde, pero la actividad quedó guardada en la base de datos.
        manager.resetForTest()
        assertTrue(!manager.state.value.isActive)
        assertTrue(manager.restoreAfterServiceRestart())
        assertEquals(id, manager.state.value.activityId)
        assertEquals(TrackingStatus.RECORDING, manager.state.value.status)
        manager.pause()
        manager.resetForTest()
        assertTrue("También se restauran actividades en pausa", manager.restoreAfterServiceRestart())
        assertEquals(TrackingStatus.PAUSED, manager.state.value.status)
        manager.discard()
    }
}
