package com.destinyy.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.destinyy.app.domain.model.JourneyLegDraft
import com.destinyy.app.service.JourneyTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var requestedStops: List<JourneyLegDraft> = emptyList()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (hasLocationPermission()) {
            if (Build.VERSION.SDK_INT >= 33 && result[Manifest.permission.POST_NOTIFICATIONS] == false) {
                Toast.makeText(
                    this,
                    "Notifications are disabled. Enable them in Settings to receive arrival alerts.",
                    Toast.LENGTH_LONG
                ).show()
            }
            startJourney()
        } else {
            Toast.makeText(this, "Location permission is needed to track your journey.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.destinyy.app.ui.theme.DestinyyTheme {
                com.destinyy.app.feature.home.HomeScreen(
                    onStart = { stops ->
                        requestedStops = stops
                        val hasLocation = hasLocationPermission()
                        val needsNotifications = Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED

                        if (!hasLocation || needsNotifications) {
                            val requested = mutableListOf<String>()
                            if (!hasLocation) {
                                requested += Manifest.permission.ACCESS_FINE_LOCATION
                                requested += Manifest.permission.ACCESS_COARSE_LOCATION
                            }
                            if (needsNotifications) requested += Manifest.permission.POST_NOTIFICATIONS
                            permissions.launch(requested.toTypedArray())
                        } else {
                            startJourney()
                        }
                    },
                    onStop = {
                        stopService(Intent(this, JourneyTrackingService::class.java))
                        Toast.makeText(this, "Journey stopped", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun startJourney() {
        if (requestedStops.isEmpty()) return

        lifecycleScope.launch {
            Toast.makeText(this@MainActivity, "Finding ${requestedStops.size} destinations…", Toast.LENGTH_SHORT).show()
            val resolvedStops = withContext(Dispatchers.IO) {
                val geocoder = if (Geocoder.isPresent()) Geocoder(this@MainActivity, Locale.getDefault()) else null
                requestedStops.map { stop ->
                    val place = try {
                        geocoder?.getFromLocationName(stop.destinationName.trim(), 1)?.firstOrNull()
                    } catch (_: Exception) {
                        null
                    }
                    stop to place
                }
            }

            val missing = resolvedStops.firstOrNull { it.second == null }
            if (missing != null) {
                Toast.makeText(
                    this@MainActivity,
                    "Couldn't find '${missing.first.destinationName}'. Check the address and try again.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val intent = Intent(this@MainActivity, JourneyTrackingService::class.java).apply {
                putStringArrayListExtra(
                    JourneyTrackingService.EXTRA_NAMES,
                    ArrayList(resolvedStops.map { it.first.destinationName.trim() })
                )
                putExtra(
                    JourneyTrackingService.EXTRA_LATS,
                    resolvedStops.map { it.second!!.latitude }.toDoubleArray()
                )
                putExtra(
                    JourneyTrackingService.EXTRA_LONS,
                    resolvedStops.map { it.second!!.longitude }.toDoubleArray()
                )
                putStringArrayListExtra(
                    JourneyTrackingService.EXTRA_MODES,
                    ArrayList(resolvedStops.map { it.first.mode })
                )
                putStringArrayListExtra(
                    JourneyTrackingService.EXTRA_ALERTS_BY_STOP,
                    ArrayList(resolvedStops.map { it.first.alertMinutes.sortedDescending().joinToString(",") })
                )
            }
            ContextCompat.startForegroundService(this@MainActivity, intent)
            Toast.makeText(
                this@MainActivity,
                "Tracking started for ${resolvedStops.size} stops",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
