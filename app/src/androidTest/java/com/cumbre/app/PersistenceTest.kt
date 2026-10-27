package com.cumbre.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cumbre.app.data.FileStore
import com.cumbre.app.data.PrepareResult
import com.cumbre.app.data.RouteRepository
import com.cumbre.app.data.db.ActivityEntity
import com.cumbre.app.data.db.ActivityPointEntity
import com.cumbre.app.data.db.CumbreDatabase
import com.cumbre.app.data.toRecorded
import com.cumbre.core.recording.ActivityStatsAccumulator
import com.cumbre.core.recording.RecoveryDecision
import com.cumbre.core.recording.RecoveryPolicy
import com.cumbre.core.recording.StoredActivityInfo
import com.cumbre.core.recording.StoredActivityState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Pruebas de persistencia con Room real (ejecutar en un dispositivo o emulador). */
@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private lateinit var context: Context
    private lateinit var db: CumbreDatabase
    private lateinit var routes: RouteRepository

    private val gpx = """
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <metadata><name>Cerro de prueba</name><link href="https://www.wikiloc.com/wikiloc/view.do?id=1"/></metadata>
          <wpt lat="-31.99" lon="-64.935"><name>Cumbre</name></wpt>
          <trk><trkseg>
            <trkpt lat="-32.0" lon="-64.92"><ele>2000</ele></trkpt>
            <trkpt lat="-31.995" lon="-64.93"><ele>2300</ele></trkpt>
            <trkpt lat="-31.99" lon="-64.935"><ele>2790</ele></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, CumbreDatabase::class.java).build()
        routes = RouteRepository(context, db.routes(), db.activities(), FileStore(context))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun prepare(): PrepareResult.Ready {
        val f = File(context.cacheDir, "test-${System.nanoTime()}.gpx").apply { writeText(gpx) }
        return routes.prepareFromFile(f, "prueba.gpx", f.length()) as PrepareResult.Ready
    }

    @Test
    fun importaGuardaYRecuperaUnaRuta() = runTest {
        val pending = prepare()
        assertEquals("https://www.wikiloc.com/wikiloc/view.do?id=1", pending.pending.detectedSourceUrl)
        val id = routes.confirmImport(pending.pending, "Mi cerro", pending.pending.detectedSourceUrl)
        val saved = routes.observeRoute(id).first()
        assertNotNull(saved)
        assertEquals("Mi cerro", saved!!.name)
        assertEquals("WIKILOC", saved.source)
        assertEquals(790.0, saved.elevationGainMeters!!, 0.01)
        assertEquals(1, db.routes().waypoints(id).size)
        val geometry = routes.loadGeometry(saved)
        assertEquals(3, geometry!!.lines.single().size)
    }

    @Test
    fun noDuplicaLaMismaRuta() = runTest {
        val id1 = routes.confirmImport(prepare().pending, "A", null)
        val second = prepare()
        assertEquals(id1, second.pending.duplicateOf?.id)
        val id2 = routes.confirmImport(second.pending, "B", null)
        assertEquals(id1, id2)
        assertEquals(1, db.routes().getAll().size)
    }

    @Test
    fun eliminarRutaBorraWaypointsYArchivo() = runTest {
        val id = routes.confirmImport(prepare().pending, "A", null)
        val r = db.routes().get(id)!!
        val file = FileStore(context).resolve(r.filePath)
        assertTrue(file.exists())
        routes.delete(r)
        assertEquals(0, db.routes().waypoints(id).size)
        assertTrue(!file.exists())
    }

    @Test
    fun actividadAbiertaSeRecuperaConSusPuntos() = runTest {
        val dao = db.activities()
        val now = System.currentTimeMillis()
        val id = dao.insert(ActivityEntity(name = "Salida", state = StoredActivityState.RECORDING.name, routeId = null, routeName = null, startedAt = now - 600_000, runningSince = now - 600_000))
        repeat(5) { i ->
            dao.insertPoint(ActivityPointEntity(activityId = id, segment = 0, latitude = -32.0 + i * 0.0005, longitude = -64.9, timeMillis = now - 500_000 + i * 60_000L, altitude = 2000.0 + i * 20, accuracy = 5.0, speed = 1.0))
        }
        dao.update(dao.get(id)!!.copy(lastPointTime = now - 260_000))
        val open = dao.findOpen()!!
        val decision = RecoveryPolicy.decide(
            StoredActivityInfo(open.id, StoredActivityState.valueOf(open.state), open.lastPointTime, open.startedAt, dao.pointCount(open.id)),
            now,
        )
        assertEquals(RecoveryDecision.OfferResume(id, wasRecording = true), decision)
        val stats = ActivityStatsAccumulator.rebuild(dao.points(id).map { it.toRecorded() }).snapshot(0)
        assertEquals(5, stats.pointCount)
        assertTrue(stats.distanceMeters > 200)
    }
}
