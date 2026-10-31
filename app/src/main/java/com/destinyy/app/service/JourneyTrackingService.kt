package com.destinyy.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.LifecycleService
import com.destinyy.app.MainActivity
import com.destinyy.app.domain.eta.GpsEtaEstimator
import com.google.android.gms.location.*

private data class TrackedStop(
    val destinationName: String,
    val latitude: Double,
    val longitude: Double,
    val mode: String,
    val alertMinutes: Set<Int>
)

class JourneyTrackingService : LifecycleService() {
    private lateinit var fused: FusedLocationProviderClient
    private var stops: List<TrackedStop> = emptyList()
    private var activeStopIndex = 0
    private val fired = mutableSetOf<Int>()
    private var previousEta: Int? = null
    private val gpsEtaEstimator = GpsEtaEstimator()
    private var previousLocation: Location? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::handleLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val active = NotificationChannel(CHANNEL, "Active journey", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing journey progress and current destination"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val alerts = NotificationChannel(ALERTS, "Arrival reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Advance reminders and arrival alerts for each journey stop"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(active)
            manager.createNotificationChannel(alerts)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val names = intent.getStringArrayListExtra(EXTRA_NAMES).orEmpty()
        val latitudes = intent.getDoubleArrayExtra(EXTRA_LATS)
        val longitudes = intent.getDoubleArrayExtra(EXTRA_LONS)
        val modes = intent.getStringArrayListExtra(EXTRA_MODES).orEmpty()
        val alertsByStop = intent.getStringArrayListExtra(EXTRA_ALERTS_BY_STOP).orEmpty()

        if (names.isEmpty() || latitudes == null || longitudes == null ||
            latitudes.size != names.size || longitudes.size != names.size || modes.size != names.size
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        stops = names.indices.map { index ->
            val selectedAlerts = alertsByStop.getOrNull(index)
                ?.split(",")
                ?.mapNotNull(String::toIntOrNull)
                ?.filter { it > 0 }
                ?.toSet()
                .orEmpty()
                .ifEmpty { setOf(10, 5, 2) }
            TrackedStop(names[index], latitudes[index], longitudes[index], modes[index], selectedAlerts)
        }
        activeStopIndex = 0
        fired.clear()
        previousEta = null
        gpsEtaEstimator.reset()
        previousLocation = null

        startForeground(NOTIFICATION_ID, ongoing("Getting location…"))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopTracking()
            return START_NOT_STICKY
        }

        // High-accuracy fused updates use GNSS/GPS where available and combine
        // other device sensors to improve stability. Tracking is foreground-only.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_500L)
            .setMinUpdateDistanceMeters(5f)
            .build()

        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {
            stopTracking()
        }
        return START_NOT_STICKY
    }

    private fun handleLocation(location: Location) {
        // Discard weak fixes rather than letting GPS drift trigger false arrivals.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCEPTABLE_ACCURACY_METERS) {
            NotificationManagerCompat.from(this).notify(
                NOTIFICATION_ID,
                ongoing("Weak GPS signal · waiting for a more accurate fix")
            )
            return
        }

        val stop = stops.getOrNull(activeStopIndex) ?: return
        val distance = FloatArray(1).also {
            Location.distanceBetween(location.latitude, location.longitude, stop.latitude, stop.longitude, it)
        }[0]

        if (distance <= ARRIVAL_RADIUS_METERS) {
            completeCurrentStop(stop)
            previousLocation = Location(location)
            return
        }

        val measuredSpeed = gpsSpeedSample(location)
        val smoothedSpeed = measuredSpeed?.let { sample ->
            gpsEtaEstimator.addSpeedSample(
                speedMetersPerSecond = sample,
                speedAccuracyMetersPerSecond = location.takeIf { it.hasSpeedAccuracy() }
                    ?.speedAccuracyMetersPerSecond?.toDouble(),
                mode = stop.mode
            )
        } ?: gpsEtaEstimator.currentSpeedMetersPerSecond

        val eta = gpsEtaEstimator.estimateMinutes(distance.toDouble(), stop.mode)
        val notificationText = if (eta != null) {
            "GPS ETA about $eta min · based on your live movement"
        } else {
            if ((smoothedSpeed ?: 0.0) < 0.8) {
                "Currently stopped · ETA updates when GPS detects movement"
            } else {
                "Calibrating GPS speed · ETA will appear shortly"
            }
        }
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            ongoing(notificationText, eta)
        )

        if (eta != null) {
            val thresholds = stop.alertMinutes.sortedDescending()
            val lastEta = previousEta
            if (lastEta == null) {
                thresholds.firstOrNull { eta <= it }?.let { threshold ->
                    thresholds.filter { it >= threshold }.forEach(fired::add)
                    sendReminder(threshold, eta)
                }
            } else {
                thresholds.forEach { threshold ->
                    if (threshold !in fired && lastEta > threshold && eta <= threshold) {
                        sendReminder(threshold, eta)
                    }
                }
            }
            previousEta = eta
        }
        previousLocation = Location(location)
    }

    /**
     * Prefer the speed reported by Android's location provider when its speed
     * accuracy is useful. Otherwise derive ground speed between consecutive
     * monotonic GPS fixes, ignoring very small movements within GPS noise.
     */
    private fun gpsSpeedSample(location: Location): Double? {
        val reportedSpeedIsUsable = location.hasSpeed() &&
            (!location.hasSpeedAccuracy() || location.speedAccuracyMetersPerSecond <= MAX_SPEED_ACCURACY_METERS_PER_SECOND)

        if (reportedSpeedIsUsable) return location.speed.toDouble()

        val previous = previousLocation ?: return null
        val elapsedSeconds = (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
        if (!elapsedSeconds.isFinite() || elapsedSeconds !in MIN_SPEED_SAMPLE_SECONDS..MAX_SPEED_SAMPLE_SECONDS) {
            return null
        }

        val travelledMeters = previous.distanceTo(location).toDouble()
        if (!travelledMeters.isFinite() || travelledMeters < MIN_MOVEMENT_METERS) return 0.0
        return travelledMeters / elapsedSeconds
    }

    private fun completeCurrentStop(stop: TrackedStop) {
        val completedIndex = activeStopIndex
        activeStopIndex += 1
        fired.clear()
        previousEta = null
        gpsEtaEstimator.reset()
        previousLocation = null

        if (activeStopIndex >= stops.size) {
            notify(
                ALERTS,
                ARRIVAL_NOTIFICATION_BASE + completedIndex,
                "Journey complete",
                "You've reached ${stop.destinationName}. All ${stops.size} stops completed.",
                true
            )
            stopTracking()
            return
        }

        val next = stops[activeStopIndex]
        notify(
            ALERTS,
            ARRIVAL_NOTIFICATION_BASE + completedIndex,
            "Stop ${completedIndex + 1} reached",
            "You've reached ${stop.destinationName}. Next: ${modeLabel(next.mode)} to ${next.destinationName}.",
            true
        )
        NotificationManagerCompat.from(this).notify(
            NOTIFICATION_ID,
            ongoing("Next: ${modeLabel(next.mode)} to ${next.destinationName}")
        )
    }

    private fun sendReminder(minutes: Int, eta: Int) {
        fired += minutes
        val stop = stops.getOrNull(activeStopIndex) ?: return
        val notificationId = REMINDER_NOTIFICATION_BASE + activeStopIndex * 100 + minutes
        notify(
            ALERTS,
            notificationId,
            "You're about $minutes minutes away",
            "${stop.destinationName} · ${modeLabel(stop.mode)} · estimate now about $eta min",
            false
        )
    }

    private fun notify(channel: String, id: Int, title: String, body: String, high: Boolean) {
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(if (high) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .apply { if (high) setDefaults(NotificationCompat.DEFAULT_ALL) }
            .build()
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun ongoing(text: String, etaMinutes: Int? = null): Notification {
        val current = stops.getOrNull(activeStopIndex)
        val stopTitle = if (current == null) {
            "Destinyy is tracking your journey"
        } else {
            "Destinyy · Stop ${activeStopIndex + 1}/${stops.size}"
        }
        val details = if (current == null) {
            text
        } else {
            "${current.destinationName} · ${modeLabel(current.mode)} · $text"
        }
        val stopAction = PendingIntent.getService(
            this,
            1,
            Intent(this, JourneyTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(stopTitle)
            .setContentText(details)
            .setSubText(if (stops.isEmpty()) "Preparing journey" else "$activeStopIndex of ${stops.size} stops completed")
            .setContentIntent(openAppIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(etaMinutes?.let { "${it.coerceAtMost(999)}m" } ?: "ACTIVE")
            .setStyle(progressStyle())
            .setProgress(stops.size.coerceAtLeast(1), activeStopIndex.coerceAtLeast(0), false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop journey", stopAction)
            .build()
    }

    private fun progressStyle(): NotificationCompat.ProgressStyle {
        val style = NotificationCompat.ProgressStyle()
        stops.forEachIndexed { index, stop ->
            val color = modeColor(stop.mode)
            style.addProgressSegment(NotificationCompat.ProgressStyle.Segment(100).setColor(color))
            style.addProgressPoint(NotificationCompat.ProgressStyle.Point((index + 1) * 100).setColor(color))
        }
        style.setProgress((activeStopIndex * 100).coerceAtMost(stops.size * 100))
        style.setProgressTrackerIcon(IconCompat.createWithResource(this, android.R.drawable.ic_menu_mylocation))
        return style
    }

    private fun modeColor(mode: String): Int = when (mode) {
        "CAB_CAR" -> Color.rgb(55, 115, 190)
        "BUS" -> Color.rgb(35, 145, 105)
        "WALKING" -> Color.rgb(190, 125, 35)
        else -> Color.rgb(115, 90, 180)
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "CAB_CAR" -> "Cab / Car"
        "BUS" -> "Bus"
        "WALKING" -> "Walking"
        else -> "Train / Metro"
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun stopTracking() {
        runCatching { fused.removeLocationUpdates(callback) }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(callback) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    companion object {
        const val EXTRA_NAMES = "destination_names"
        const val EXTRA_LATS = "destination_latitudes"
        const val EXTRA_LONS = "destination_longitudes"
        const val EXTRA_MODES = "journey_modes"
        const val EXTRA_ALERTS_BY_STOP = "alert_minutes_by_stop"
        const val ACTION_STOP = "destinyy.stop"
        const val CHANNEL = "destinyy_active"
        const val ALERTS = "destinyy_alerts"
        const val NOTIFICATION_ID = 2401
        private const val ARRIVAL_RADIUS_METERS = 150f
        private const val REMINDER_NOTIFICATION_BASE = 2500
        private const val ARRIVAL_NOTIFICATION_BASE = 7000
        private const val MAX_ACCEPTABLE_ACCURACY_METERS = 60f
        private const val MAX_SPEED_ACCURACY_METERS_PER_SECOND = 3f
        private const val MIN_MOVEMENT_METERS = 3.0
        private const val MIN_SPEED_SAMPLE_SECONDS = 2.0
        private const val MAX_SPEED_SAMPLE_SECONDS = 30.0
    }
}
