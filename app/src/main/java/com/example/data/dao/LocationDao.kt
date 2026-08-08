package com.example.data.dao

import androidx.room.*
import com.example.data.model.CheckpointReport
import com.example.data.model.SavedLocation
import com.example.data.model.SavedRoute
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY timestamp DESC")
    fun getAllSavedLocations(): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)

    @Query("SELECT * FROM checkpoints WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveCheckpoints(): Flow<List<CheckpointReport>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoints(checkpoints: List<CheckpointReport>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoint(checkpoint: CheckpointReport)

    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    fun getAllSavedRoutes(): Flow<List<SavedRoute>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: SavedRoute)
}
