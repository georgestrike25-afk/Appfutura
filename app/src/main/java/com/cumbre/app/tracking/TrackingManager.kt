package com.cumbre.app.tracking

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.cumbre.app.data.RouteRepository
import com.cumbre.app.data.SettingsRepository
import com.cumbre.app.data.db.ActivityDao
import com.cumbre.app.data.db.ActivityEntity
import com.cumbre.app.data.db.ActivityPointEntity
import com.cumbre.app.data.toRecorded
import com.cumbre.core.analysis.AnalysisCodec
import com.cumbre.core.analysis.ProfilePoint
import com.cumbre.core.geo.GeoPoint
import com.cumbre.core.navigation.EtaEstimator
import com.cumbre.core.navigation.FollowState
import com.cumbre.core.navigation.RouteFollower
import com.cumbre.core.navigation.TrackGeometry
import com.cumbre.core.recording.ActivityClock
import com.cumbre.core.recording.ActivityStatsAccumulator
import com.cumbre.core.recording.ActivityStatsSnapshot
import com.cumbre.core.recording.FilterDecision
import com.cumbre.core.recording.GpsSignal
import com.cumbre.core.recording.LocationFilter
import com.cumbre.core.recording.LocationSample
import com.cumbre.core.recording.RecordedPoint
import com.cumbre.core.recording.RecoveryDecision
import com.cumbre.core.recording.RecoveryPolicy
import com.cumbre.core.recording.SignalMonitor
import com.cumbre.core.recording.StoredActivityInfo
import com.cumbre.core.recording.StoredActivityState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TrackingStatus { IDLE, RECORDING, PAUSED }

data class FollowedRoute(
    val id: Long,
    val name: String,
    val lines: List<List<GeoPoint>>,
    val waypoints: List<Pair<String?, GeoPoint>>,
    val totalMeters: Double,
    /** Perfil de elevación de la ruta, para estimar el desnivel que falta. */
    val profile: List<ProfilePoint> = emptyList(),
)

data class TrackingState(
    val status: TrackingStatus = TrackingStatus.IDLE,
    val activityId: Long? = null,
    val activityName: String = "",
    val route: FollowedRoute? = null,
    val recordedLines: List<List<GeoPoint>> = emptyList(),
    val stats: ActivityStatsSnapshot = ActivityStatsSnapshot(0.0, 0, 0, 0.0, 0.0, 0.0, 0),
    val elapsedMillis: Long = 0,
    /** Última lectura recibida (aunque se haya descartado por imprecisa). */
    val lastFix: LocationSample? = null,
    val signal: GpsSignal = GpsSignal.SEARCHING,
    val follow: FollowState? = null,
    val currentSpeedMps: Double? = null,
    /** Tiempo estimado para llegar al final de la ruta. */
    val etaSeconds: Long? = null,
    /** Tiempo estimado hasta el próximo waypoint. */
    val etaToWaypointSeconds: Long? = null,
    /** Se recuperó una actividad interrumpida por el sistema. */
    val recovered: Boolean = false,
) {
    val isActive: Boolean get() = status != TrackingStatus.IDLE
}

data class OffRouteEvent(val distanceMeters: Double)

/**
 * Estado de la actividad en curso, compartido por todas las pantallas y por el servicio en primer
 * plano. Cada punto aceptado se guarda inmediatamente en Room, de modo que la actividad puede
 * recuperarse si Android cierra el proceso.
 */
class TrackingManager(
    private val context: Context,
    private val activityDao: ActivityDao,
    private val routes: RouteRepository,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _offRoute = MutableSharedFlow<OffRouteEvent>(extraBufferCapacity = 4)
    val offRouteEvents: SharedFlow<OffRouteEvent> = _offRoute.asSharedFlow()

    private var activity: ActivityEntity? = null
    private var clock = ActivityClock()
    private var filter = LocationFilter()
    private var accumulator = ActivityStatsAccumulator()
    private var follower: RouteFollower? = null
    private val recorded = ArrayList<ArrayList<GeoPoint>>()
    private var lastPersist = 0L
    private var tickerJob: Job? = null
    private var offRouteEnabled = true

    init {
        scope.launch {
            settings.settings.collect { s ->
                offRouteEnabled = s.offRouteAlertEnabled
                follower?.offRouteThresholdMeters = s.offRouteThresholdMeters.toDouble()
            }
        }
    }

    // ---------- Acciones del usuario ----------

    /** Inicia una actividad libre o siguiendo la ruta [routeId]. */
    suspend fun start(routeId: Long?): Long = mutex.withLock {
        check(_state.value.status == TrackingStatus.IDLE) { "Ya hay una actividad en curso" }
        val now = System.currentTimeMillis()
        val route = routeId?.let { loadFollowedRoute(it) }
        val name = route?.name ?: "Recorrido del ${SimpleDateFormat("d/M HH:mm", Locale.forLanguageTag("es-AR")).format(Date(now))}"
        val entity = ActivityEntity(
            name = name,
            state = StoredActivityState.RECORDING.name,
            routeId = route?.id,
            routeName = route?.name,
            startedAt = now,
            runningSince = now,
        )
        val id = withContext(Dispatchers.IO) { activityDao.insert(entity) }
        activity = entity.copy(id = id)
        clock = ActivityClock().start(now)
        filter = LocationFilter()
        accumulator = ActivityStatsAccumulator()
        recorded.clear()
        recorded.add(ArrayList())
        follower = route?.let { buildFollower(it) }
        _state.value = TrackingState(
            status = TrackingStatus.RECORDING,
            activityId = id,
            activityName = name,
            route = route,
        )
        routeId?.let { routes.markOpened(it) }
        startService()
        startTicker()
        id
    }

    suspend fun pause() = mutex.withLock {
        val a = activity ?: return@withLock
        if (_state.value.status != TrackingStatus.RECORDING) return@withLock
        val now = System.currentTimeMillis()
        clock = clock.pause(now)
        filter.breakSegment()
        persist(a.copy(state = StoredActivityState.PAUSED.name), now)
        _state.update { it.copy(status = TrackingStatus.PAUSED, elapsedMillis = clock.elapsed(now)) }
        // El servicio sigue vivo en pausa (sin GPS) para que Android no cierre la app: así se
        // puede reanudar desde la notificación y la actividad no se pierde.
    }

    suspend fun resume() = mutex.withLock {
        val a = activity ?: return@withLock
        if (_state.value.status != TrackingStatus.PAUSED) return@withLock
        val now = System.currentTimeMillis()
        clock = clock.start(now)
        val next = a.copy(state = StoredActivityState.RECORDING.name, currentSegment = a.currentSegment + 1)
        filter.breakSegment()
        recorded.add(ArrayList())
        persist(next, now)
        _state.update { it.copy(status = TrackingStatus.RECORDING, recovered = false) }
        startService()
        startTicker()
    }

    /** Finaliza y guarda la actividad. Devuelve su id. */
    suspend fun finish(): Long? = mutex.withLock {
        val a = activity ?: return@withLock null
        val now = System.currentTimeMillis()
        clock = clock.pause(now)
        persist(a.copy(state = StoredActivityState.FINISHED.name, endedAt = now), now)
        reset()
        a.id
    }

    /** Descarta la actividad en curso y todos sus puntos. */
    suspend fun discard() = mutex.withLock {
        val a = activity ?: return@withLock
        withContext(Dispatchers.IO) { activityDao.delete(a.id) }
        reset()
    }

    // ---------- Recuperación ----------

    suspend fun checkRecovery(): RecoveryDecision {
        if (_state.value.isActive) return RecoveryDecision.Nothing
        val open = withContext(Dispatchers.IO) { activityDao.findOpen() } ?: return RecoveryDecision.Nothing
        val count = withContext(Dispatchers.IO) { activityDao.pointCount(open.id) }
        val info = StoredActivityInfo(
            id = open.id,
            state = runCatching { StoredActivityState.valueOf(open.state) }.getOrDefault(StoredActivityState.PAUSED),
            lastPointTimeMillis = open.lastPointTime,
            startedAtMillis = open.startedAt,
            pointCount = count,
        )
        return RecoveryPolicy.decide(info, System.currentTimeMillis())
    }

    /**
     * Restaura una actividad abierta a partir de la base de datos. Si [continueRecording] es
     * verdadero (reinicio del servicio por el sistema) sigue grabando; si no, queda en pausa
     * para que el usuario decida.
     */
    suspend fun restore(activityId: Long, continueRecording: Boolean): Boolean = mutex.withLock {
        if (_state.value.isActive) return@withLock true
        val a = withContext(Dispatchers.IO) { activityDao.get(activityId) } ?: return@withLock false
        if (a.state == StoredActivityState.FINISHED.name) return@withLock false
        val points = withContext(Dispatchers.IO) { activityDao.points(a.id).map { it.toRecorded() } }
        val now = System.currentTimeMillis()
        // El tiempo en que la app estuvo cerrada no se cuenta como tiempo activo.
        val accumulated = a.accumulatedMillis + if (a.runningSince != null && a.lastPointTime != null) {
            (a.lastPointTime - a.runningSince).coerceAtLeast(0)
        } else {
            0
        }
        clock = ActivityClock(accumulated, null)
        accumulator = ActivityStatsAccumulator.rebuild(points)
        filter = LocationFilter()
        recorded.clear()
        points.groupBy { it.segment }.toSortedMap().values.forEach { seg ->
            recorded.add(ArrayList(seg.map { GeoPoint(it.latitude, it.longitude) }))
        }
        val route = a.routeId?.let { loadFollowedRoute(it) }
        follower = route?.let { buildFollower(it) }
        var restored = a.copy(currentSegment = a.currentSegment + 1, accumulatedMillis = accumulated, runningSince = null)
        val status = if (continueRecording) {
            clock = clock.start(now)
            restored = restored.copy(state = StoredActivityState.RECORDING.name, runningSince = now)
            TrackingStatus.RECORDING
        } else {
            restored = restored.copy(state = StoredActivityState.PAUSED.name)
            TrackingStatus.PAUSED
        }
        recorded.add(ArrayList())
        activity = restored
        withContext(Dispatchers.IO) { activityDao.update(restored) }
        _state.value = TrackingState(
            status = status,
            activityId = a.id,
            activityName = a.name,
            route = route,
            recordedLines = recorded.map { it.toList() },
            stats = accumulator.snapshot(clock.elapsed(now)),
            elapsedMillis = clock.elapsed(now),
            recovered = true,
        )
        if (continueRecording) startTicker()
        true
    }

    /** Cierra una actividad abandonada conservando sus puntos. */
    suspend fun finishStored(activityId: Long) = withContext(Dispatchers.IO) {
        val a = activityDao.get(activityId) ?: return@withContext
        val points = activityDao.points(a.id).map { it.toRecorded() }
        val snap = ActivityStatsAccumulator.rebuild(points).snapshot(a.accumulatedMillis)
        activityDao.update(
            a.copy(
                state = StoredActivityState.FINISHED.name,
                endedAt = a.lastPointTime ?: a.startedAt,
                runningSince = null,
                distanceMeters = snap.distanceMeters,
                elevationGainMeters = snap.elevationGainMeters,
                elevationLossMeters = snap.elevationLossMeters,
                movingMillis = snap.movingMillis,
                maxSpeedMps = snap.maxSpeedMps,
                pointCount = snap.pointCount,
            ),
        )
    }

    suspend fun discardStored(activityId: Long) = withContext(Dispatchers.IO) { activityDao.delete(activityId) }

    /** Llamado por el servicio cuando Android lo reinicia tras cerrar el proceso. */
    suspend fun restoreAfterServiceRestart(): Boolean {
        if (_state.value.isActive) return true
        val open = withContext(Dispatchers.IO) { activityDao.findOpen() } ?: return false
        return when (open.state) {
            StoredActivityState.RECORDING.name -> restore(open.id, continueRecording = true)
            StoredActivityState.PAUSED.name -> restore(open.id, continueRecording = false)
            else -> false
        }
    }

    // ---------- Ubicación ----------

    fun onLocation(sample: LocationSample) {
        scope.launch { mutex.withLock { handleLocation(sample) } }
    }

    private suspend fun handleLocation(sample: LocationSample) {
        val current = _state.value
        val now = System.currentTimeMillis()
        if (current.status != TrackingStatus.RECORDING) {
            _state.update { it.copy(lastFix = sample, signal = signalFor(sample, now)) }
            return
        }
        val a = activity ?: return
        val follow = follower?.update(sample.geo, sample.accuracyMeters) ?: current.follow
        if (follow?.offRouteStarted == true && offRouteEnabled) _offRoute.tryEmit(OffRouteEvent(follow.distanceToTrackMeters))
        val (eta, etaWaypoint) = estimateArrival(current.route, follow)
        when (val decision = filter.process(sample)) {
            is FilterDecision.Accept -> {
                val point = RecordedPoint(
                    segment = a.currentSegment,
                    latitude = sample.latitude,
                    longitude = sample.longitude,
                    timeMillis = sample.timeMillis,
                    altitudeMeters = sample.altitudeMeters,
                    accuracyMeters = sample.accuracyMeters,
                    speedMps = sample.speedMps,
                )
                withContext(Dispatchers.IO) {
                    activityDao.insertPoint(
                        ActivityPointEntity(
                            activityId = a.id, segment = point.segment, latitude = point.latitude, longitude = point.longitude,
                            timeMillis = point.timeMillis, altitude = point.altitudeMeters, accuracy = point.accuracyMeters, speed = point.speedMps,
                        ),
                    )
                }
                accumulator.add(point, decision.distanceMeters)
                if (recorded.isEmpty()) recorded.add(ArrayList())
                recorded.last().add(sample.geo)
                activity = a.copy(lastPointTime = sample.timeMillis)
                if (now - lastPersist > PERSIST_EVERY_MILLIS) persist(activity!!, now)
                _state.update {
                    it.copy(
                        lastFix = sample,
                        signal = signalFor(sample, now),
                        follow = follow,
                        recordedLines = recorded.map { line -> line.toList() },
                        stats = accumulator.snapshot(clock.elapsed(now)),
                        elapsedMillis = clock.elapsed(now),
                        currentSpeedMps = sample.speedMps,
                        etaSeconds = eta,
                        etaToWaypointSeconds = etaWaypoint,
                    )
                }
            }
            is FilterDecision.Reject -> _state.update {
                it.copy(
                    lastFix = sample,
                    signal = signalFor(sample, now),
                    follow = follow,
                    currentSpeedMps = sample.speedMps ?: it.currentSpeedMps,
                    etaSeconds = eta ?: it.etaSeconds,
                    etaToWaypointSeconds = etaWaypoint ?: it.etaToWaypointSeconds,
                )
            }
        }
    }

    /** Tiempo estimado al final y al próximo waypoint, según desnivel pendiente y ritmo propio. */
    private fun estimateArrival(route: FollowedRoute?, follow: FollowState?): Pair<Long?, Long?> {
        if (route == null || follow == null) return null to null
        val progress = follow.progressMeters
        val moving = accumulator.snapshot(0).movingMillis
        val done = EtaEstimator.remainingClimb(route.profile, 0.0, progress)
        val toEnd = EtaEstimator.estimateSeconds(
            remainingMeters = follow.remainingMeters,
            remaining = EtaEstimator.remainingClimb(route.profile, progress),
            doneMeters = progress,
            done = done,
            movingMillis = moving,
        )
        val wp = follow.nextWaypoint
        val wpDistance = follow.distanceToNextWaypointMeters
        val toWaypoint = if (wp != null && wpDistance != null) {
            EtaEstimator.estimateSeconds(
                remainingMeters = wpDistance,
                remaining = EtaEstimator.remainingClimb(route.profile, progress, wp.progressMeters),
                doneMeters = progress,
                done = done,
                movingMillis = moving,
            )
        } else {
            null
        }
        return toEnd to toWaypoint
    }

    private fun signalFor(sample: LocationSample, now: Long) =
        SignalMonitor.evaluate(sample.timeMillis.coerceAtMost(now), sample.accuracyMeters, now, filter.maxAccuracyMeters)

    // ---------- Internos ----------

    private suspend fun loadFollowedRoute(routeId: Long): FollowedRoute? {
        val route = routes.getRoute(routeId) ?: return null
        val geometry = routes.loadGeometry(route) ?: return null
        val waypoints = geometry.waypoints.map { it.name to it.geo }
        return FollowedRoute(route.id, route.name, geometry.lines, waypoints, route.distanceMeters, AnalysisCodec.decodeProfile(route.profile))
    }

    private suspend fun buildFollower(route: FollowedRoute): RouteFollower {
        val threshold = settings.current().offRouteThresholdMeters.toDouble()
        return RouteFollower(TrackGeometry(route.lines), route.waypoints, offRouteThresholdMeters = threshold)
    }

    private suspend fun persist(a: ActivityEntity, now: Long) {
        val snap = accumulator.snapshot(clock.elapsed(now))
        val updated = a.copy(
            accumulatedMillis = clock.accumulatedMillis,
            runningSince = clock.runningSinceMillis,
            distanceMeters = snap.distanceMeters,
            elevationGainMeters = snap.elevationGainMeters,
            elevationLossMeters = snap.elevationLossMeters,
            movingMillis = snap.movingMillis,
            maxSpeedMps = snap.maxSpeedMps,
            pointCount = snap.pointCount,
        )
        activity = updated
        lastPersist = now
        withContext(Dispatchers.IO) { activityDao.update(updated) }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && _state.value.status == TrackingStatus.RECORDING) {
                val now = System.currentTimeMillis()
                _state.update { s ->
                    val fix = s.lastFix
                    s.copy(
                        elapsedMillis = clock.elapsed(now),
                        signal = SignalMonitor.evaluate(fix?.timeMillis?.coerceAtMost(now), fix?.accuracyMeters, now, filter.maxAccuracyMeters),
                    )
                }
                delay(1_000)
            }
        }
    }

    private fun reset() {
        tickerJob?.cancel()
        activity = null
        follower = null
        recorded.clear()
        clock = ActivityClock()
        _state.value = TrackingState()
        stopService()
    }

    private fun startService() {
        val intent = Intent(context, TrackingService::class.java)
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }

    private fun stopService() {
        context.stopService(Intent(context, TrackingService::class.java))
    }

    companion object {
        private const val PERSIST_EVERY_MILLIS = 15_000L
    }
}
