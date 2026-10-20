package com.destinyy.app.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val modes = listOf("TRAIN_METRO" to "Train / Metro", "CAB_CAR" to "Cab / Car", "BUS" to "Bus", "WALKING" to "Walking")

@Composable
fun HomeScreen(onStart: (String, String, Set<Int>) -> Unit, onStop: () -> Unit) {
    var destination by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("TRAIN_METRO") }
    var alerts by remember { mutableStateOf(setOf(10, 5, 2)) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Default.LocationOn, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("Destinyy", style = MaterialTheme.typography.headlineMedium)
                    Text("Your destination. Right on time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Where are you going?", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Destination or address") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        singleLine = true,
                        placeholder = { Text("e.g. Andheri Railway Station") }
                    )
                    Text("Travel mode", style = MaterialTheme.typography.titleMedium)
                    modes.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (key, label) ->
                                FilterChip(selected = mode == key, onClick = { mode = key }, label = { Text(label) })
                            }
                        }
                    }
                }
            }
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.NotificationsActive, null)
                        Text("Remind me before arrival", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 5, 2).forEach { mins ->
                            FilterChip(
                                selected = mins in alerts,
                                onClick = { alerts = if (mins in alerts) alerts - mins else alerts + mins },
                                label = { Text("$mins min") }
                            )
                        }
                    }
                    Text("Estimates are approximate until route-based ETA is integrated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = { onStart(destination.trim(), mode, alerts) },
                enabled = destination.isNotBlank() && alerts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Start journey") }
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text("Stop tracking") }
            Text("Location is used only while a journey is active.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
