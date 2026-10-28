package com.cumbre.app

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.cumbre.app.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Recorre la app real en el emulador y guarda capturas de cada pantalla en
 * `Android/data/com.cumbre.app/files/capturas/` (el workflow `emulador.yml` las publica).
 * Si el workflow dejó `argentina.map` y `relieve-cordoba.zip` en `files/`, se instalan antes
 * para que el mapa y el relieve se vean como en el teléfono.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val app = context.applicationContext as CumbreApp
    private val container get() = app.container
    private val device = UiDevice.getInstance(instrumentation)
    private val outDir = File(context.getExternalFilesDir(null), "capturas").apply { mkdirs() }

    private fun shot(name: String, settleMillis: Long = 2_500) {
        device.waitForIdle()
        Thread.sleep(settleMillis)
        device.takeScreenshot(File(outDir, "$name.png"))
    }

    private fun launch(openTracking: Boolean = false) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (openTracking) intent.putExtra(MainActivity.EXTRA_OPEN_TRACKING, true)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 10_000)
        Thread.sleep(2_000)
    }

    private fun tap(text: String, timeout: Long = 8_000): Boolean {
        val o = device.wait(Until.findObject(By.text(text)), timeout) ?: return false
        o.click()
        return true
    }

    @Test
    fun capturarPantallas() = runBlocking {
        // 1. Primer uso: bienvenida e inicio sin rutas.
        launch()
        shot("00-bienvenida")
        container.settings.setOnboardingDone()
        container.settings.setBatteryGuideSeen()
        launch()
        shot("01-inicio-sin-rutas")
        tap("Rutas"); shot("02-rutas-vacia")

        // 2. Datos: ruta de prueba y, si están, mapa y relieve reales.
        val routeId = TestRoute.ensureImported(container, context.cacheDir)
        val files = context.getExternalFilesDir(null)!!
        File(files, "argentina.map").takeIf { it.exists() }?.let { f ->
            println("CUMBRE_TEST mapa: " + container.maps.importMap(Uri.fromFile(f)) { _, _ -> })
            f.delete()
        }
        File(files, "relieve-cordoba.zip").takeIf { it.exists() }?.let { f ->
            println("CUMBRE_TEST relieve: " + container.relief.import(Uri.fromFile(f)))
            f.delete()
        }

        launch()
        shot("03-inicio")
        tap("Mapa"); shot("04-mapa", 6_000)
        tap("Rutas"); shot("05-rutas")
        if (tap(TestRoute.NAME)) {
            shot("06-detalle-ruta", 5_000)
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30)
            shot("07-detalle-ruta-abajo")
            device.pressBack()
        }
        tap("Más"); shot("08-mas")
        if (tap("Mapas sin conexión")) { shot("09-mapas-offline"); device.pressBack() }
        if (tap("Ajustes")) {
            shot("10-ajustes")
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30)
            shot("11-ajustes-abajo")
            device.pressBack()
        }

        // 3. Navegación siguiendo la ruta (posiciones simuladas por el workflow).
        val manager = container.trackingManager
        if (manager.state.value.isActive) manager.discard()
        manager.start(routeId)
        launch(openTracking = true)
        val end = System.currentTimeMillis() + 60_000
        while (manager.state.value.follow == null && System.currentTimeMillis() < end) Thread.sleep(1_000)
        Thread.sleep(15_000)
        shot("12-navegacion", 4_000)
        launch()
        shot("13-inicio-grabando")
        manager.pause()
        launch(openTracking = true)
        shot("14-navegacion-pausa", 4_000)
        manager.resume()
        Thread.sleep(5_000)
        val activityId = manager.finish()
        launch()
        tap("Actividad"); shot("15-actividad")
        if (activityId != null && tap(TestRoute.NAME)) shot("16-actividad-finalizada", 5_000)

        // 4. Modo oscuro.
        container.settings.setThemeMode(ThemeMode.DARK)
        launch()
        shot("17-inicio-oscuro")
        tap("Mapa"); shot("18-mapa-oscuro", 6_000)
        tap("Rutas"); shot("19-rutas-oscuro")
        container.settings.setThemeMode(ThemeMode.SYSTEM)
    }
}
