package com.cumbre.app.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.cumbre.app.CumbreApp
import com.cumbre.app.MainActivity
import com.cumbre.app.R
import com.cumbre.core.format.Formatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano de tipo «location»: mantiene el GPS activo mientras se graba una
 * actividad (libre o siguiendo una ruta), aunque la pantalla esté apagada, la app en segundo
 * plano o se la quite de recientes. Corre desde que se inicia la actividad hasta que el usuario
 * la finaliza o la descarta; en pausa sigue vivo pero sin GPS.
 *
 * Blindaje: wake lock parcial mientras graba (algunos fabricantes duermen la CPU con la
 * pantalla apagada aunque haya un servicio en primer plano) y un vigilante que vuelve a
 * registrar el GPS si deja de llegar señal.
 */
class TrackingService : LifecycleService() {
    private val container by lazy { (application as CumbreApp).container }
    private var locationJob: Job? = null
    private var observersStarted = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastSampleAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PAUSE -> lifecycleScope.launch { container.trackingManager.pause() }
            ACTION_RESUME -> lifecycleScope.launch { container.trackingManager.resume() }
        }
        if (!container.locationSource.hasAnyPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val started = runCatching {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification("Buscando señal GPS…"), type)
        }.isSuccess
        if (!started) {
            // Android puede impedir iniciar el servicio desde segundo plano: la actividad queda
            // guardada y se ofrecerá recuperarla al abrir la app.
            stopSelf()
            return START_NOT_STICKY
        }
        val manager = container.trackingManager
        if (intent == null) {
            // Reinicio automático del sistema después de cerrar el proceso.
            lifecycleScope.launch {
                if (!manager.restoreAfterServiceRestart()) stopSelf()
            }
        }
        startObservers()
        return START_STICKY
    }

    private fun startObservers() {
        if (observersStarted) return
        observersStarted = true
        val manager = container.trackingManager
        lifecycleScope.launch {
            // El GPS solo se usa mientras se graba; en pausa se libera para ahorrar batería.
            manager.state.map { it.status == TrackingStatus.RECORDING }.distinctUntilChanged().collect { recording ->
                if (recording) {
                    acquireWakeLock()
                    startLocation()
                } else {
                    locationJob?.cancel()
                    locationJob = null
                    releaseWakeLock()
                }
            }
        }
        lifecycleScope.launch {
            // Se detiene solo cuando la actividad termina (finalizada o descartada).
            manager.state.first { it.isActive }
            manager.state.first { !it.isActive }
            stopSelf()
        }
        lifecycleScope.launch {
            while (true) {
                val s = manager.state.value
                if (s.isActive) {
                    val paused = s.status == TrackingStatus.PAUSED
                    val text = buildString {
                        if (paused) append("En pausa · ")
                        append(Formatters.distance(s.stats.distanceMeters))
                        append(" · ")
                        append(Formatters.clock(s.elapsedMillis))
                        s.follow?.let { append(" · quedan ${Formatters.distance(it.remainingMeters)}") }
                    }
                    runCatching { notificationManager().notify(NOTIFICATION_ID, buildNotification(text, paused)) }
                }
                if (s.status == TrackingStatus.RECORDING && lastSampleAt > 0 &&
                    System.currentTimeMillis() - lastSampleAt > GPS_WATCHDOG_MILLIS
                ) {
                    // Algunos teléfonos cortan las actualizaciones sin avisar: se vuelven a pedir.
                    startLocation()
                }
                delay(5_000)
            }
        }
        lifecycleScope.launch {
            manager.offRouteEvents.collect { event -> alertOffRoute(event.distanceMeters) }
        }
    }

    private fun startLocation() {
        val manager = container.trackingManager
        locationJob?.cancel()
        lastSampleAt = System.currentTimeMillis()
        locationJob = lifecycleScope.launch {
            container.locationSource.updates().collect {
                lastSampleAt = System.currentTimeMillis()
                manager.onLocation(it)
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cumbre:grabacion").apply {
            setReferenceCounted(false)
            // Límite de seguridad: se renueva al reanudar; una actividad de más de 12 h sigue
            // grabando gracias al servicio en primer plano aunque el wake lock expire.
            runCatching { acquire(WAKE_LOCK_MAX_MILLIS) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
    }

    /** Quitar la app de recientes no detiene la grabación: solo el usuario la finaliza. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    private fun alertOffRoute(distance: Double) {
        vibrate()
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Te desviaste del recorrido")
            .setContentText("Estás a ${Formatters.distance(distance)} del track.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        runCatching { notificationManager().notify(ALERT_ID, notification) }
    }

    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)) }
    }

    private fun buildNotification(text: String, paused: Boolean = false): Notification {
        val toggle = if (paused) {
            NotificationCompat.Action(0, "Reanudar", serviceIntent(ACTION_RESUME, 1))
        } else {
            NotificationCompat.Action(0, "Pausar", serviceIntent(ACTION_PAUSE, 2))
        }
        return NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(container.trackingManager.state.value.activityName.ifBlank { "Recorrido en curso" })
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(toggle)
            .build()
    }

    private fun serviceIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this,
            code,
            Intent(this, TrackingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TRACKING, true)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        locationJob?.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_ALERTS = "alerts"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_ID = 1002
        const val ACTION_PAUSE = "com.cumbre.app.action.PAUSE"
        const val ACTION_RESUME = "com.cumbre.app.action.RESUME"
        private const val GPS_WATCHDOG_MILLIS = 90_000L
        private const val WAKE_LOCK_MAX_MILLIS = 12 * 60 * 60 * 1000L

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_TRACKING, context.getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_tracking_desc)
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = context.getString(R.string.channel_alerts_desc)
                    enableVibration(true)
                },
            )
        }
    }
}
