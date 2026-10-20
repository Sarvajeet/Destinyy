package com.destinyy.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.destinyy.app.MainActivity
import com.google.android.gms.location.*

class JourneyTrackingService : LifecycleService() {
    private lateinit var fused: FusedLocationProviderClient
    private var destinationName = "destination"
    private var destLat = 0.0
    private var destLon = 0.0
    private var mode = "TRAIN_METRO"
    private var thresholds = listOf(10, 5, 2)
    private val fired = mutableSetOf<Int>()
    private var arrived = false
    private var previousEta: Int? = null
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) { result.lastLocation?.let(::handleLocation) }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Active journey", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(ALERTS, "Arrival reminders", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopTracking(); return START_NOT_STICKY }
        destinationName = intent?.getStringExtra(EXTRA_NAME) ?: destinationName
        destLat = intent?.getDoubleExtra(EXTRA_LAT, destLat) ?: destLat
        destLon = intent?.getDoubleExtra(EXTRA_LON, destLon) ?: destLon
        mode = intent?.getStringExtra(EXTRA_MODE) ?: mode
        thresholds = intent?.getIntegerArrayListExtra(EXTRA_ALERTS)?.distinct()?.sortedDescending() ?: listOf(10, 5, 2)
        fired.clear(); arrived = false; previousEta = null
        startForeground(NOTIFICATION_ID, ongoing("Getting location…"))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) { stopTracking(); return START_NOT_STICKY }
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L).setMinUpdateIntervalMillis(15_000L).setMinUpdateDistanceMeters(40f).build()
        try { fused.requestLocationUpdates(request, callback, mainLooper) } catch (_: SecurityException) { stopTracking() }
        return START_NOT_STICKY
    }

    private fun handleLocation(location: Location) {
        if (arrived) return
        val distance = FloatArray(1).also { Location.distanceBetween(location.latitude, location.longitude, destLat, destLon, it) }[0]
        if (distance <= 150f) {
            arrived = true
            notify(ALERTS, 2600, "You've reached your destination", destinationName, true)
            stopTracking(); return
        }
        val eta = com.destinyy.app.domain.eta.EtaEstimator.minutesFromStraightLineMeters(distance.toDouble(), mode)
        NotificationManagerCompatShim.update(this, ongoing("About $eta min away · approximate estimate"))
        if (previousEta == null) {
            thresholds.filter { eta <= it }.maxOrNull()?.let { threshold ->
                thresholds.filter { it >= threshold }.forEach(fired::add)
                sendReminder(threshold, eta)
            }
        } else thresholds.forEach { if (it !in fired && previousEta!! > it && eta <= it) sendReminder(it, eta) }
        previousEta = eta
    }

    private fun sendReminder(minutes: Int, eta: Int) {
        fired += minutes
        notify(ALERTS, 2500 + minutes, "You're about $minutes minutes away", "$destinationName · estimate now about $eta min", false)
    }

    private fun notify(channel: String, id: Int, title: String, body: String, high: Boolean) {
        val notification = NotificationCompat.Builder(this, channel).setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle(title).setContentText(body).setAutoCancel(true).setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT).build()
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun ongoing(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, JourneyTrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_map).setContentTitle("Destinyy is tracking your journey").setContentText(text).setContentIntent(open).addAction(0, "Stop", stop).setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun stopTracking() {
        runCatching { fused.removeLocationUpdates(callback) }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }
    override fun onDestroy() { runCatching { fused.removeLocationUpdates(callback) }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)

    companion object {
        const val EXTRA_NAME = "destination_name"; const val EXTRA_LAT = "destination_lat"; const val EXTRA_LON = "destination_lon"
        const val EXTRA_MODE = "journey_mode"; const val EXTRA_ALERTS = "alert_minutes"; const val ACTION_STOP = "destinyy.stop"
        const val CHANNEL = "destinyy_active"; const val ALERTS = "destinyy_alerts"; const val NOTIFICATION_ID = 2401
    }
}

private object NotificationManagerCompatShim {
    fun update(context: android.content.Context, notification: Notification) {
        androidx.core.app.NotificationManagerCompat.from(context).notify(JourneyTrackingService.NOTIFICATION_ID, notification)
    }
}
