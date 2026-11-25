package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.GpsAnthiMainScreen
import com.example.ui.theme.GpsSetterTheme
import com.example.ui.viewmodel.GpsAnthiViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GpsSetterTheme {
                val context = LocalContext.current
                val viewModel: GpsAnthiViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { _ ->
                    viewModel.refreshGpsAlignment()
                }

                LaunchedEffect(Unit) {
                    val requiredPermissions = buildList {
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    val missing = requiredPermissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        locationPermissionLauncher.launch(missing.toTypedArray())
                    }
                }

                GpsAnthiMainScreen(
                    uiState = uiState,
                    onVerifyRootClicked = viewModel::onVerifyRootClicked,
                    onControlModeChanged = viewModel::onControlModeChanged,
                    onLatInputChanged = viewModel::onLatInputChanged,
                    onLngInputChanged = viewModel::onLngInputChanged,
                    onApplyCoordinatesClicked = viewModel::onApplyCoordinatesClicked,
                    onSetPointA = viewModel::onSetPointAFromPin,
                    onSetPointB = viewModel::onSetPointBFromPin,
                    onCalculateRoadRoute = viewModel::onCalculateRoadRoute,
                    onClearRoute = viewModel::onClearRoute,
                    onTraversalModeChanged = viewModel::onTraversalModeChanged,
                    onSpeedChanged = viewModel::onSpeedChanged,
                    onPinMoved = viewModel::onMapPinSelected,
                    onRecenterClicked = viewModel::onRecenterClicked,
                    onZoomInClicked = viewModel::onZoomInClicked,
                    onZoomOutClicked = viewModel::onZoomOutClicked,
                    onZoomChangedFromMap = viewModel::onZoomChangedFromMap,
                    onPlayClicked = viewModel::onPlayClicked,
                    onStopClicked = viewModel::onStopClicked,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
