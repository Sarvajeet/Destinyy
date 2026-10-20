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
import com.destinyy.app.service.JourneyTrackingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var destinationText = ""
    private var selectedMode = "TRAIN_METRO"
    private var selectedAlerts = setOf(10, 5, 2)
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true || it[Manifest.permission.ACCESS_COARSE_LOCATION] == true) startJourney()
        else Toast.makeText(this, "Location permission is needed to track your journey.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            com.destinyy.app.ui.theme.DestinyyTheme {
                com.destinyy.app.feature.home.HomeScreen(
                    onStart = { destination, mode, alerts ->
                        destinationText = destination; selectedMode = mode; selectedAlerts = alerts
                        val needed = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
                        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
                        if (missing.isEmpty()) startJourney() else permissions.launch(missing.toTypedArray())
                    },
                    onStop = { stopService(Intent(this, JourneyTrackingService::class.java)); Toast.makeText(this, "Journey stopped", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startJourney() {
        if (destinationText.isBlank()) return
        lifecycleScope.launch {
            val place = withContext(Dispatchers.IO) {
                try { if (Geocoder.isPresent()) Geocoder(this@MainActivity, Locale.getDefault()).getFromLocationName(destinationText, 1)?.firstOrNull() else null }
                catch (_: Exception) { null }
            }
            if (place == null) {
                Toast.makeText(this@MainActivity, "Place not found. Try a fuller address or landmark.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val intent = Intent(this@MainActivity, JourneyTrackingService::class.java).apply {
                putExtra(JourneyTrackingService.EXTRA_NAME, destinationText)
                putExtra(JourneyTrackingService.EXTRA_LAT, place.latitude)
                putExtra(JourneyTrackingService.EXTRA_LON, place.longitude)
                putExtra(JourneyTrackingService.EXTRA_MODE, selectedMode)
                putIntegerArrayListExtra(JourneyTrackingService.EXTRA_ALERTS, ArrayList(selectedAlerts.sortedDescending()))
            }
            ContextCompat.startForegroundService(this@MainActivity, intent)
            Toast.makeText(this@MainActivity, "Journey tracking started", Toast.LENGTH_SHORT).show()
        }
    }
}
