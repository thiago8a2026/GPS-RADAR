package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.database.AppDatabase
import com.example.data.model.CheckpointReport
import com.example.data.model.SavedLocation
import com.example.navigation.LatLngPoint
import com.example.navigation.SimulationEngine
import com.example.root.RootManager
import com.example.ui.screens.DashboardScreen
import com.example.ui.theme.GpsSetterTheme
import com.example.zygisk.ZygiskHookEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        database = AppDatabase.getInstance(applicationContext)

        // Pre-populate sample checkpoints if empty
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = database.locationDao()
            val existingCheckpoints = dao.getActiveCheckpoints()
            dao.insertCheckpoints(
                listOf(
                    CheckpointReport(
                        id = "cp-1",
                        title = "Retén Policial - Av. Javier Prado",
                        latitude = -12.086374,
                        longitude = -77.032793,
                        reporterHwid = "hwid_demo_1",
                        upvotes = 12
                    ),
                    CheckpointReport(
                        id = "cp-2",
                        title = "Control MTC / SUNAT - Peaje",
                        latitude = -12.016374,
                        longitude = -77.062793,
                        reporterHwid = "hwid_demo_2",
                        upvotes = 8
                    )
                )
            )
            dao.insertLocation(
                SavedLocation(
                    title = "Base Operativa Principal",
                    category = "Work",
                    latitude = -12.046374,
                    longitude = -77.042793,
                    address = "Av. Central 123"
                )
            )
        }

        setContent {
            GpsSetterTheme {
                var currentPos by remember { mutableStateOf(LatLngPoint(-12.046374, -77.042793)) }
                var isSimulating by remember { mutableStateOf(false) }
                var simSpeedKmh by remember { mutableStateOf(45f) }

                val savedLocations by database.locationDao().getAllSavedLocations()
                    .collectAsState(initial = emptyList())
                val checkpoints by database.locationDao().getActiveCheckpoints()
                    .collectAsState(initial = emptyList())

                val isRootAvailable = remember { RootManager.isRootAvailable() }
                val zygiskStatus = remember { ZygiskHookEngine.getStatus() }

                // Simulation loop
                LaunchedEffect(isSimulating, currentPos, simSpeedKmh) {
                    if (isSimulating) {
                        while (true) {
                            val jittered = SimulationEngine.applyJitter(currentPos)
                            currentPos = jittered
                            delay(1000)
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        currentPos = currentPos,
                        isSimulating = isSimulating,
                        simSpeedKmh = simSpeedKmh,
                        isRootAvailable = isRootAvailable,
                        isZygiskActive = zygiskStatus.isZygiskActive,
                        savedLocations = savedLocations,
                        checkpoints = checkpoints,
                        onStartSimulation = { isSimulating = true },
                        onStopSimulation = { isSimulating = false },
                        onUpdateSpeed = { simSpeedKmh = it },
                        onMapClick = { target -> currentPos = target },
                        onJoystickMove = { dx, dy ->
                            currentPos = SimulationEngine.moveByJoystick(currentPos, dx, dy, simSpeedKmh)
                        },
                        onAddCheckpoint = { title, pos ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                database.locationDao().insertCheckpoint(
                                    CheckpointReport(
                                        id = "cp-${System.currentTimeMillis()}",
                                        title = title,
                                        latitude = pos.latitude,
                                        longitude = pos.longitude,
                                        reporterHwid = RootManager.getHardwareId()
                                    )
                                )
                            }
                        },
                        onSaveLocation = { title, cat, pos ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                database.locationDao().insertLocation(
                                    SavedLocation(
                                        title = title,
                                        category = cat,
                                        latitude = pos.latitude,
                                        longitude = pos.longitude
                                    )
                                )
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

