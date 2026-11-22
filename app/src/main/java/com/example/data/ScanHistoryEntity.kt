package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey val id: String,
    val produceName: String,
    val ripenessStage: String,
    val ripenessScore: Int,
    val analysisJson: String,
    val localImagePath: String?,
    val sampleKey: String?,
    val scannedAtMillis: Long
)
