package com.cumbre.app.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Servicio en primer plano de tipo «location»: mantiene el GPS activo mientras se graba una
 * actividad, aunque la pantalla esté apagada o la app en segundo plano. Solo corre mientras
 * hay una actividad grabando; al pausar o finalizar se detiene y libera el GPS.
 */
class TrackingService : LifecycleService() {
    private val container by lazy { (application as CumbreApp).container }
    private var locationJob: Job? = null
    private var observersStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
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
        locationJob = lifecycleScope.launch {
            container.locationSource.updates().collect { manager.onLocation(it) }
        }
        lifecycleScope.launch {
            // Se detiene cuando la actividad deja de grabar (pausa, fin o descarte).
            manager.state.first { it.status == TrackingStatus.RECORDING }
            manager.state.first { it.status != TrackingStatus.RECORDING }
            stopSelf()
        }
        lifecycleScope.launch {
            while (true) {
                val s = manager.state.value
                if (s.status == TrackingStatus.RECORDING) {
                    val text = buildString {
                        append(Formatters.distance(s.stats.distanceMeters))
                        append(" · ")
                        append(Formatters.clock(s.elapsedMillis))
                        s.follow?.let { append(" · quedan ${Formatters.distance(it.remainingMeters)}") }
                    }
                    notificationManager().notify(NOTIFICATION_ID, buildNotification(text))
                }
                delay(5_000)
            }
        }
        lifecycleScope.launch {
            manager.offRouteEvents.collect { event -> alertOffRoute(event.distanceMeters) }
        }
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

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(container.trackingManager.state.value.activityName.ifBlank { "Recorrido en curso" })
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TRACKING, true)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    override fun onDestroy() {
        locationJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_ALERTS = "alerts"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_ID = 1002

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
