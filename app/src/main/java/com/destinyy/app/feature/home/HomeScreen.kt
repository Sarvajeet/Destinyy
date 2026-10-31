package com.destinyy.app.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.destinyy.app.domain.model.JourneyLegDraft

private const val MAX_STOPS = 8
private val modes = listOf(
    "TRAIN_METRO" to "Train / Metro",
    "CAB_CAR" to "Cab / Car",
    "BUS" to "Bus",
    "WALKING" to "Walking"
)

@Composable
fun HomeScreen(onStart: (List<JourneyLegDraft>) -> Unit, onStop: () -> Unit) {
    var stops by remember { mutableStateOf(listOf(JourneyLegDraft())) }

    fun updateStop(index: Int, transform: (JourneyLegDraft) -> JourneyLegDraft) {
        stops = stops.mapIndexed { current, stop ->
            if (current == index) transform(stop) else stop
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text("Destinyy", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Your destination. Right on time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Plan your journey", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Add every interchange or pit stop in the order you'll reach it. Each stop gets its own transport mode and arrival reminders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                stops.forEachIndexed { index, stop ->
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Stop ${index + 1}", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (index == stops.lastIndex) "Final destination" else "Pit stop / interchange",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (stops.size > 1) {
                                    IconButton(onClick = { stops = stops.filterIndexed { i, _ -> i != index } }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove stop ${index + 1}")
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = stop.destinationName,
                                onValueChange = { value -> updateStop(index) { it.copy(destinationName = value) } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Destination or address") },
                                leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                                singleLine = true,
                                placeholder = { Text("e.g. Andheri Railway Station") }
                            )

                            Text("How will you reach this stop?", style = MaterialTheme.typography.titleSmall)
                            modes.chunked(2).forEach { modeRow ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    modeRow.forEach { (key, label) ->
                                        FilterChip(
                                            selected = stop.mode == key,
                                            onClick = { updateStop(index) { it.copy(mode = key) } },
                                            label = { Text(label) }
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Arrival reminders", style = MaterialTheme.typography.titleSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(10, 5, 2).forEach { minutes ->
                                    FilterChip(
                                        selected = minutes in stop.alertMinutes,
                                        onClick = {
                                            updateStop(index) {
                                                val updated = if (minutes in it.alertMinutes) {
                                                    it.alertMinutes - minutes
                                                } else {
                                                    it.alertMinutes + minutes
                                                }
                                                it.copy(alertMinutes = updated)
                                            }
                                        },
                                        label = { Text("$minutes min") }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { stops = stops + JourneyLegDraft() },
                enabled = stops.size < MAX_STOPS,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (stops.size < MAX_STOPS) "Add another stop" else "Maximum 8 stops")
            }

            Button(
                onClick = { onStart(stops) },
                enabled = stops.all { it.destinationName.isNotBlank() && it.alertMinutes.isNotEmpty() },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Start ${stops.size}-stop journey")
            }
            OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text("Stop tracking")
            }
            Text(
                "GPS ETA uses precise location and your measured movement speed. It may pause while you are stopped or GPS signal is weak. Road-route and live-traffic routing are not included yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
