package com.example.data

import android.content.Context
import android.util.Log
import com.example.ai.ProduceVisionAiService
import com.example.model.ChatMessage
import com.example.model.ProduceAnalysisResult
import com.example.util.ImageUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "ScanHistoryRepository"

class ScanHistoryRepository(
    private val dao: ScanHistoryDao,
    private val chatDao: ChatMessageDao
) {

    val recentScans: Flow<List<ProduceAnalysisResult>> = dao.observeAllScans().map { entities ->
        entities.mapNotNull { entity ->
            try {
                ProduceVisionAiService.parseAnalysisJson(
                    jsonText = entity.analysisJson,
                    id = entity.id,
                    localImagePath = entity.localImagePath,
                    sampleKey = entity.sampleKey,
                    scannedAtMillis = entity.scannedAtMillis
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed cached scan ${entity.id}", e)
                null
            }
        }
    }

    suspend fun saveScan(result: ProduceAnalysisResult) {
        val entity = ScanHistoryEntity(
            id = result.id,
            produceName = result.produceName,
            ripenessStage = result.ripenessStage.name,
            ripenessScore = result.ripenessScore,
            analysisJson = ProduceVisionAiService.serializeAnalysisToJson(result),
            localImagePath = result.localImagePath,
            sampleKey = result.sampleKey,
            scannedAtMillis = result.scannedAtMillis
        )
        dao.insertScan(entity)
    }

    suspend fun deleteScan(id: String) {
        val imagePath = dao.getImagePathById(id)
        dao.deleteScanById(id)
        chatDao.deleteMessagesForScan(id)
        ImageUtils.deleteScanFile(imagePath)
    }

    suspend fun cleanupOrphanedImages(context: Context): Int {
        val activePaths = dao.getAllSavedImagePaths().toSet()
        return ImageUtils.cleanupOrphanScanFiles(context, activePaths)
    }

    suspend fun clearAllHistory(context: Context) {
        dao.clearAllScans()
        chatDao.clearAllMessages()
        ImageUtils.clearAllScanFiles(context)
    }

    suspend fun getChatMessages(scanId: String): List<ChatMessage> {
        return chatDao.getMessagesForScan(scanId).map { entity ->
            ChatMessage(
                id = entity.id,
                scanId = entity.scanId,
                isUser = entity.isUser,
                text = entity.text,
                isStreaming = false,
                createdAtMillis = entity.createdAtMillis
            )
        }
    }

    suspend fun saveChatMessage(message: ChatMessage) {
        if (message.scanId.isBlank() || message.text.isBlank()) return
        chatDao.insertMessage(
            ChatMessageEntity(
                id = message.id,
                scanId = message.scanId,
                isUser = message.isUser,
                text = message.text,
                createdAtMillis = message.createdAtMillis
            )
        )
    }
}
