package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navigation.LatLngPoint
import com.example.navigation.RouteTraversalMode
import com.example.root.RootStatus
import com.example.ui.components.OsmMapView
import com.example.ui.theme.ActiveGreen
import com.example.ui.theme.CardSurface
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ControlTabMode
import com.example.ui.viewmodel.GpsAnthiUiState
import java.util.Locale

@Composable
fun GpsAnthiMainScreen(
    uiState: GpsAnthiUiState,
    onVerifyRootClicked: () -> Unit,
    onControlModeChanged: (ControlTabMode) -> Unit,
    onLatInputChanged: (String) -> Unit,
    onLngInputChanged: (String) -> Unit,
    onApplyCoordinatesClicked: () -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onCalculateRoadRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onTraversalModeChanged: (RouteTraversalMode) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onPinMoved: (LatLngPoint, Boolean) -> Unit,
    onRecenterClicked: () -> Unit,
    onZoomInClicked: () -> Unit,
    onZoomOutClicked: () -> Unit,
    onZoomChangedFromMap: (Double) -> Unit,
    onPlayClicked: () -> Unit,
    onStopClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Mapa OpenStreetMap (osmdroid) a pantalla completa con polilínea vial
        OsmMapView(
            targetPoint = uiState.targetPoint,
            routePoints = uiState.routePoints,
            pointA = if (uiState.controlMode == ControlTabMode.ROAD_ROUTE) uiState.pointA else null,
            pointB = if (uiState.controlMode == ControlTabMode.ROAD_ROUTE) uiState.pointB else null,
            zoomLevel = uiState.zoomLevel,
            recenterNonce = uiState.recenterNonce,
            isSimulating = uiState.isSimulating,
            onPinMoved = onPinMoved,
            onZoomChanged = onZoomChangedFromMap,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Barra superior flotante: GPS ANTHI + Estado Root + Alineación GPS + Botón SU
        TopStatusCard(
            rootStatus = uiState.rootDiagnostics.status,
            rootStatusLabel = uiState.rootStatusLabel,
            alignmentSummary = uiState.gpsAlignment?.summary,
            isAligned = uiState.gpsAlignment?.isFullyAligned == true,
            onVerifyRootClicked = onVerifyRootClicked,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
        )

        // 3. Panel inferior flotante + controles rápidos de mapa
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            MapQuickControls(
                onZoomInClicked = onZoomInClicked,
                onZoomOutClicked = onZoomOutClicked,
                onRecenterClicked = onRecenterClicked
            )

            Spacer(modifier = Modifier.height(10.dp))

            BottomControlCard(
                uiState = uiState,
                onControlModeChanged = onControlModeChanged,
                onLatInputChanged = onLatInputChanged,
                onLngInputChanged = onLngInputChanged,
                onApplyCoordinatesClicked = {
                    focusManager.clearFocus()
                    onApplyCoordinatesClicked()
                },
                onSetPointA = onSetPointA,
                onSetPointB = onSetPointB,
                onCalculateRoadRoute = onCalculateRoadRoute,
                onClearRoute = onClearRoute,
                onTraversalModeChanged = onTraversalModeChanged,
                onSpeedChanged = onSpeedChanged,
                onPlayClicked = {
                    focusManager.clearFocus()
                    onPlayClicked()
                },
                onStopClicked = {
                    focusManager.clearFocus()
                    onStopClicked()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TopStatusCard(
    rootStatus: RootStatus,
    rootStatusLabel: String,
    alignmentSummary: String?,
    isAligned: Boolean,
    onVerifyRootClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (rootStatus) {
        RootStatus.GRANTED -> ActiveGreen
        RootStatus.CHECKING -> WarningAmber
        RootStatus.DENIED, RootStatus.NOT_ROOTED, RootStatus.TIMEOUT -> CriticalRed
    }

    Card(
        modifier = modifier.testTag("top_status_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, CardSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GPS ANTHI",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (rootStatus == RootStatus.CHECKING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = WarningAmber
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        }
                        Text(
                            text = rootStatusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("tv_root_status")
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = onVerifyRootClicked,
                    enabled = rootStatus != RootStatus.CHECKING,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CardSurface,
                        contentColor = CyberCyan
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_check_root")
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Verificar acceso root SU",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SU", fontWeight = FontWeight.Bold)
                }
            }

            if (alignmentSummary != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alignmentSummary,
                    fontSize = 11.sp,
                    color = if (isAligned) ActiveGreen.copy(alpha = 0.9f) else WarningAmber,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MapQuickControls(
    onZoomInClicked: () -> Unit,
    onZoomOutClicked: () -> Unit,
    onRecenterClicked: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(
            onClick = onZoomInClicked,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = DarkSurface.copy(alpha = 0.94f),
                contentColor = CyberCyan
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("btn_zoom_in")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Acercar mapa")
        }

        FilledTonalIconButton(
            onClick = onZoomOutClicked,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = DarkSurface.copy(alpha = 0.94f),
                contentColor = CyberCyan
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("btn_zoom_out")
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Alejar mapa")
        }

        FilledTonalIconButton(
            onClick = onRecenterClicked,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = CyberCyan,
                contentColor = DeepSpace
            ),
            modifier = Modifier
                .size(48.dp)
                .testTag("btn_recenter_pin")
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Centrar mapa en pin")
        }
    }
}

@Composable
private fun BottomControlCard(
    uiState: GpsAnthiUiState,
    onControlModeChanged: (ControlTabMode) -> Unit,
    onLatInputChanged: (String) -> Unit,
    onLngInputChanged: (String) -> Unit,
    onApplyCoordinatesClicked: () -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onCalculateRoadRoute: () -> Unit,
    onClearRoute: () -> Unit,
    onTraversalModeChanged: (RouteTraversalMode) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onPlayClicked: () -> Unit,
    onStopClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (uiState.isSimulating) ActiveGreen.copy(alpha = 0.7f) else CardSurface
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = CyberCyan,
        unfocusedBorderColor = CardSurface,
        focusedLabelColor = CyberCyan,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = CyberCyan,
        errorBorderColor = CriticalRed,
        errorLabelColor = CriticalRed
    )

    Card(
        modifier = modifier.testTag("bottom_control_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.95f)),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Selector compacto: Punto Fijo vs Ruta Vial OSM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.controlMode == ControlTabMode.FIXED_POINT,
                    onClick = { onControlModeChanged(ControlTabMode.FIXED_POINT) },
                    label = { Text("Punto Fijo") },
                    leadingIcon = {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan,
                        selectedLeadingIconColor = CyberCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_fixed_point")
                )

                FilterChip(
                    selected = uiState.controlMode == ControlTabMode.ROAD_ROUTE,
                    onClick = { onControlModeChanged(ControlTabMode.ROAD_ROUTE) },
                    label = { Text("Ruta Calles") },
                    leadingIcon = {
                        Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                        selectedLabelColor = CyberCyan,
                        selectedLeadingIconColor = CyberCyan
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_road_route")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.controlMode == ControlTabMode.FIXED_POINT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.latInput,
                        onValueChange = onLatInputChanged,
                        label = { Text("Latitud") },
                        singleLine = true,
                        isError = uiState.coordinateError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        ),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("et_lat")
                    )

                    OutlinedTextField(
                        value = uiState.lngInput,
                        onValueChange = onLngInputChanged,
                        label = { Text("Longitud") },
                        singleLine = true,
                        isError = uiState.coordinateError != null,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onApplyCoordinatesClicked() }
                        ),
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("et_lng")
                    )
                }

                AnimatedVisibility(visible = uiState.coordinateError != null) {
                    Text(
                        text = uiState.coordinateError.orEmpty(),
                        color = CriticalRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onApplyCoordinatesClicked,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_apply_coords")
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Centrar pin en coordenadas", fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Controles de Ruta Vial (Punto A -> Punto B por calles OSM)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSetPointA,
                        enabled = !uiState.isSimulating,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_set_point_a")
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fijar A", fontSize = 12.sp, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = onSetPointB,
                        enabled = !uiState.isSimulating,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ActiveGreen.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ActiveGreen),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_set_point_b")
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fijar B y Trazar", fontSize = 12.sp, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RouteTraversalMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.traversalMode == mode,
                            onClick = { onTraversalModeChanged(mode) },
                            enabled = !uiState.isSimulating,
                            label = { Text(mode.label, fontSize = 11.sp, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ActiveGreen.copy(alpha = 0.2f),
                                selectedLabelColor = ActiveGreen
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${uiState.speedKmh.toInt()} km/h",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Slider(
                        value = uiState.speedKmh,
                        onValueChange = onSpeedChanged,
                        valueRange = 5f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (uiState.isCalculatingRoute) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = CyberCyan)
                        Text("Calculando ruta por calles OSM...", color = TextSecondary, fontSize = 12.sp)
                    }
                } else if (uiState.routePoints.size >= 2) {
                    val km = uiState.routeDistanceMeters / 1000.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${uiState.routeSourceLabel ?: "Ruta"} • ${String.format(Locale.US, "%.2f km", km)}",
                            color = ActiveGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (!uiState.isSimulating) {
                            OutlinedButton(
                                onClick = onCalculateRoadRoute,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text("Recalcular", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = onClearRoute,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text("Limpiar", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayClicked,
                    enabled = !uiState.isSimulating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActiveGreen,
                        contentColor = DeepSpace,
                        disabledContainerColor = ActiveGreen.copy(alpha = 0.25f),
                        disabledContentColor = TextSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_play")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Iniciar simulación GPS",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onStopClicked,
                    enabled = uiState.isSimulating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = CriticalRed,
                        contentColor = TextPrimary,
                        disabledContainerColor = CardSurface,
                        disabledContentColor = TextSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_stop")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Detener simulación GPS",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DeepSpace.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isSimulating) ActiveGreen else TextSecondary)
                        )
                        Text(
                            text = uiState.spoofStatusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isSimulating) ActiveGreen else TextSecondary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("tv_spoof_state")
                        )
                    }

                    AnimatedVisibility(visible = uiState.feedbackMessage != null) {
                        Text(
                            text = uiState.feedbackMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = WarningAmber,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
