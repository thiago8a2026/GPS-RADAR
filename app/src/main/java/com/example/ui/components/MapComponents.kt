package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.navigation.LatLngPoint
import com.example.ui.theme.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Mock Map Radar Component simulating real-time GPS coordinates,
 * route vectors, and nearby police/checkpoint markers.
 */
@Composable
fun MapRadarView(
    currentPos: LatLngPoint,
    targetDestination: LatLngPoint?,
    checkpoints: List<LatLngPoint>,
    isSimulating: Boolean,
    onMapClick: (LatLngPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        // Map interaction simulation
                        val width = size.width
                        val height = size.height
                        val touchX = change.position.x
                        val touchY = change.position.y

                        val deltaLat = (height / 2 - touchY) / 50000.0
                        val deltaLng = (touchX - width / 2) / 50000.0
                        onMapClick(
                            LatLngPoint(
                                currentPos.latitude + deltaLat,
                                currentPos.longitude + deltaLng
                            )
                        )
                    }
                }
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width.coerceAtMost(size.height) / 2 * 0.85f

            // Draw concentric radar rings
            drawCircle(
                color = CyberCyan.copy(alpha = 0.08f),
                radius = maxRadius,
                center = center
            )
            drawCircle(
                color = CyberCyan.copy(alpha = 0.15f),
                radius = maxRadius * 0.66f,
                center = center
            )
            drawCircle(
                color = CyberCyan.copy(alpha = 0.25f),
                radius = maxRadius * 0.33f,
                center = center
            )

            // Draw crosshairs
            drawLine(
                color = CyberCyan.copy(alpha = 0.2f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 2f
            )
            drawLine(
                color = CyberCyan.copy(alpha = 0.2f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 2f
            )

            // Draw Route Vector to Target Destination
            targetDestination?.let { target ->
                val deltaLat = target.latitude - currentPos.latitude
                val deltaLng = target.longitude - currentPos.longitude
                val targetOffset = Offset(
                    center.x + (deltaLng * 50000).toFloat(),
                    center.y - (deltaLat * 50000).toFloat()
                )

                // Route Line
                drawLine(
                    color = CyberCyan,
                    start = center,
                    end = targetOffset,
                    strokeWidth = 4f
                )

                // Destination Pin
                drawCircle(
                    color = WarningAmber,
                    radius = 12f,
                    center = targetOffset
                )
            }

            // Draw Checkpoints
            checkpoints.forEach { cp ->
                val deltaLat = cp.latitude - currentPos.latitude
                val deltaLng = cp.longitude - currentPos.longitude
                val cpOffset = Offset(
                    center.x + (deltaLng * 50000).toFloat(),
                    center.y - (deltaLat * 50000).toFloat()
                )
                drawCircle(
                    color = CriticalRed,
                    radius = 10f,
                    center = cpOffset
                )
            }

            // Draw Current Position Pulsing Marker
            drawCircle(
                color = if (isSimulating) ActiveGreen else ElectricBlue,
                radius = 16f,
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = center
            )
        }

        // Overlay Radar Info Badge
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DeepSpace.copy(alpha = 0.8f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isSimulating) ActiveGreen else WarningAmber)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isSimulating) "SPOOFING ACTIVE" else "GPS READY",
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Lat / Lng Display
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DeepSpace.copy(alpha = 0.85f))
                .padding(8.dp)
        ) {
            Text(
                text = "Lat: %.6f".format(currentPos.latitude),
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Lng: %.6f".format(currentPos.longitude),
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Low Latency Floating Joystick Pad Overlay Component
 */
@Composable
fun JoystickPad(
    onJoystickMove: (dx: Float, dy: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val radius = 60.dp

    Box(
        modifier = modifier
            .size(130.dp)
            .clip(CircleShape)
            .background(DarkSurface.copy(alpha = 0.9f))
            .border(2.dp, CyberCyan.copy(alpha = 0.6f), CircleShape)
            .testTag("joystick_pad")
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        onJoystickMove(0f, 0f)
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        onJoystickMove(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = dragOffset.x + dragAmount.x
                        val newY = dragOffset.y + dragAmount.y
                        val maxDistance = 90f

                        val angle = atan2(newY, newX)
                        val dist = (newX * newX + newY * newY).coerceAtMost(maxDistance * maxDistance)
                        val clampedX = kotlin.math.sqrt(dist) * cos(angle)
                        val clampedY = kotlin.math.sqrt(dist) * sin(angle)

                        dragOffset = Offset(clampedX, clampedY)
                        onJoystickMove(clampedX / maxDistance, -clampedY / maxDistance)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Center Thumb Knob
        Box(
            modifier = Modifier
                .offset(x = (dragOffset.x / 3).dp, y = (dragOffset.y / 3).dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(CyberCyan)
                .border(2.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = "Joystick Steering Knob",
                tint = DeepSpace,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
