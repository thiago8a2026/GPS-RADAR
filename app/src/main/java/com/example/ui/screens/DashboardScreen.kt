package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.backend.BackendArchitecture
import com.example.data.model.CheckpointReport
import com.example.data.model.SavedLocation
import com.example.navigation.LatLngPoint
import com.example.root.RootManager
import com.example.ui.components.JoystickPad
import com.example.ui.components.MapRadarView
import com.example.ui.theme.*
import com.example.zygisk.ZygiskHookEngine

@Composable
fun DashboardScreen(
    currentPos: LatLngPoint,
    isSimulating: Boolean,
    simSpeedKmh: Float,
    isRootAvailable: Boolean,
    isZygiskActive: Boolean,
    savedLocations: List<SavedLocation>,
    checkpoints: List<CheckpointReport>,
    onStartSimulation: () -> Unit,
    onStopSimulation: () -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onMapClick: (LatLngPoint) -> Unit,
    onJoystickMove: (dx: Float, dy: Float) -> Unit,
    onAddCheckpoint: (String, LatLngPoint) -> Unit,
    onSaveLocation: (String, String, LatLngPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Radar & Controls, 1: Checkpoints, 2: Saved Places, 3: System Status

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(16.dp)
    ) {
        // App Bar & Root Indicator Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "GPS SETTER PRO",
                    color = CyberCyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stealth Root Simulation Engine",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            // Status Badges
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(
                    label = if (isRootAvailable) "ROOT OK" else "NO ROOT",
                    isActive = isRootAvailable
                )
                StatusBadge(
                    label = if (isZygiskActive) "ZYGISK" else "NO HOOK",
                    isActive = isZygiskActive
                )
            }
        }

        // Navigation Tab Selector
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = CyberCyan,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, CardSurface, RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Radar & Nav", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Retenes (${checkpoints.size})", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Places", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Root & System", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> RadarControlTab(
                currentPos = currentPos,
                isSimulating = isSimulating,
                simSpeedKmh = simSpeedKmh,
                checkpoints = checkpoints.map { LatLngPoint(it.latitude, it.longitude) },
                onStartSimulation = onStartSimulation,
                onStopSimulation = onStopSimulation,
                onUpdateSpeed = onUpdateSpeed,
                onMapClick = onMapClick,
                onJoystickMove = onJoystickMove
            )
            1 -> CheckpointsTab(
                checkpoints = checkpoints,
                currentPos = currentPos,
                onAddCheckpoint = onAddCheckpoint
            )
            2 -> SavedPlacesTab(
                savedLocations = savedLocations,
                currentPos = currentPos,
                onSaveLocation = onSaveLocation,
                onSelectLocation = { onMapClick(LatLngPoint(it.latitude, it.longitude)) }
            )
            3 -> SystemStatusTab(
                isRootAvailable = isRootAvailable,
                isZygiskActive = isZygiskActive
            )
        }
    }
}

@Composable
fun RadarControlTab(
    currentPos: LatLngPoint,
    isSimulating: Boolean,
    simSpeedKmh: Float,
    checkpoints: List<LatLngPoint>,
    onStartSimulation: () -> Unit,
    onStopSimulation: () -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onMapClick: (LatLngPoint) -> Unit,
    onJoystickMove: (dx: Float, dy: Float) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 140.dp)
        ) {
            // Radar Map View
            MapRadarView(
                currentPos = currentPos,
                targetDestination = null,
                checkpoints = checkpoints,
                isSimulating = isSimulating,
                onMapClick = onMapClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Speed Control Slider
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CardSurface, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Simulation Speed",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "%.0f km/h".format(simSpeedKmh),
                            color = CyberCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = simSpeedKmh,
                        onValueChange = onUpdateSpeed,
                        valueRange = 5f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Simulation Action Toggle Button
            Button(
                onClick = { if (isSimulating) onStopSimulation() else onStartSimulation() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSimulating) CriticalRed else ActiveGreen
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("toggle_simulation_button")
            ) {
                Icon(
                    imageVector = if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = DeepSpace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSimulating) "STOP SPOOFING" else "START GPS SPOOFING",
                    color = DeepSpace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        // Overlay Low-Latency Joystick
        JoystickPad(
            onJoystickMove = onJoystickMove,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp, end = 8.dp)
        )
    }
}

@Composable
fun CheckpointsTab(
    checkpoints: List<CheckpointReport>,
    currentPos: LatLngPoint,
    onAddCheckpoint: (String, LatLngPoint) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Community Police Checkpoints",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = "Report Retén",
                    tint = DeepSpace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Report Retén", color = DeepSpace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(checkpoints) { cp ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = cp.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Lat: %.4f | Lng: %.4f".format(cp.latitude, cp.longitude),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ThumbUp,
                                contentDescription = "Upvote",
                                tint = ActiveGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${cp.upvotes}",
                                color = ActiveGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Report Community Checkpoint / Retén", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Checkpoint Description") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onAddCheckpoint(newTitle, currentPos)
                            newTitle = ""
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Report", color = DeepSpace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun SavedPlacesTab(
    savedLocations: List<SavedLocation>,
    currentPos: LatLngPoint,
    onSaveLocation: (String, String, LatLngPoint) -> Unit,
    onSelectLocation: (SavedLocation) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Work") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Saved Work Points & Clients",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Button(
                onClick = { showSaveDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BookmarkAdd,
                    contentDescription = "Save Location",
                    tint = DeepSpace
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Current", color = DeepSpace, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(savedLocations) { loc ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLocation(loc) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = loc.title,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${loc.category} • Lat: %.4f, Lng: %.4f".format(
                                        loc.latitude,
                                        loc.longitude
                                    ),
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Go",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Current Location", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title (e.g. Base Central)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (Work/Client/Favorite)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyberCyan)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSaveLocation(title, category, currentPos)
                            title = ""
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
                ) {
                    Text("Save", color = DeepSpace)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun SystemStatusTab(
    isRootAvailable: Boolean,
    isZygiskActive: Boolean
) {
    val zygiskStatus = ZygiskHookEngine.getStatus()
    val hwid = remember { RootManager.getHardwareId() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "System Root & Zygisk Hook Architecture",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Device Hardware ID (HWID Lock)", color = TextSecondary, fontSize = 12.sp)
                    Text(hwid, color = CyberCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Magisk / KernelSU SuperUser Status", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = if (isRootAvailable) "AUTHENTICATED (UID = 0)" else "NOT ROOTED",
                        color = if (isRootAvailable) ActiveGreen else CriticalRed,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Zygisk Native Hook Module", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = "Native Engine ${zygiskStatus.nativeEngineVersion} Active (${zygiskStatus.injectedProcessCount} Processes Hooked)",
                        color = ActiveGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Backend Node.js / PostGIS Spec",
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "REST API Endpoint: https://api.gpssetterpro.internal/v1\n" +
                                "Database: PostgreSQL 16 + PostGIS extension\n" +
                                "JWT Session HWID Restriction: Active\n" +
                                "Geofencing WebSockets: Connected",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(label: String, isActive: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isActive) ActiveGreen.copy(alpha = 0.2f) else CriticalRed.copy(alpha = 0.2f))
            .border(
                1.dp,
                if (isActive) ActiveGreen else CriticalRed,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = if (isActive) ActiveGreen else CriticalRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
