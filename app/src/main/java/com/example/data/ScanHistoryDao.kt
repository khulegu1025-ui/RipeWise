package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY scannedAtMillis DESC")
    fun observeAllScans(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT localImagePath FROM scan_history WHERE id = :id")
    suspend fun getImagePathById(id: String): String?

    @Query("SELECT localImagePath FROM scan_history WHERE localImagePath IS NOT NULL")
    suspend fun getAllSavedImagePaths(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(entity: ScanHistoryEntity)

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteScanById(id: String)

    @Query("DELETE FROM scan_history")
    suspend fun clearAllScans()
}
