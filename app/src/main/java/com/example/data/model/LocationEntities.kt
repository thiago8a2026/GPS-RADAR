package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val category: String, // "Work", "Client", "Checkpoint", "Favorite"
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "checkpoints")
data class CheckpointReport(
    @PrimaryKey val id: String, // Server UUID or local ID
    val title: String, // e.g. "Control Policial - Av. Central"
    val latitude: Double,
    val longitude: Double,
    val reporterHwid: String,
    val upvotes: Int = 1,
    val downvotes: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_routes")
data class SavedRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val speedKmh: Float,
    val waypointsJson: String, // Serialized list of coordinates
    val createdAt: Long = System.currentTimeMillis()
)
