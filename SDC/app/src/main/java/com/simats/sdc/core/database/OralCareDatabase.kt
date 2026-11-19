package com.simats.sdc.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "screening_drafts")
data class ScreeningDraftEntity(
    @PrimaryKey val draftId: String,
    val idempotencyKey: String,
    val patientNamePreview: String,
    val currentStep: Int,
    val payloadJson: String,
    val syncStatus: String, // DRAFT, PENDING_SYNC, SYNCED, FAILED
    val lastError: String? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

@Entity(tableName = "pending_image_uploads")
data class PendingImageUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val draftId: String,
    val remoteScreeningId: Int? = null,
    val clinicalRegion: String,
    val subRegion: String? = null,
    val localFilePath: String,
    val syncStatus: String = "PENDING", // PENDING, UPLOADED, FAILED
    val lastError: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

@Dao
interface ScreeningDraftDao {
    @Query("SELECT * FROM screening_drafts ORDER BY updatedAtMillis DESC")
    fun observeAllDrafts(): Flow<List<ScreeningDraftEntity>>

    @Query("SELECT * FROM screening_drafts WHERE syncStatus IN ('PENDING_SYNC', 'FAILED') ORDER BY updatedAtMillis ASC")
    suspend fun getPendingSyncDrafts(): List<ScreeningDraftEntity>

    @Query("SELECT * FROM screening_drafts WHERE draftId = :draftId LIMIT 1")
    suspend fun getById(draftId: String): ScreeningDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: ScreeningDraftEntity)

    @Query("DELETE FROM screening_drafts WHERE draftId = :draftId")
    suspend fun deleteById(draftId: String)

    @Query("DELETE FROM screening_drafts")
    suspend fun clearAll()
}

@Dao
interface PendingImageDao {
    @Query("SELECT * FROM pending_image_uploads WHERE draftId = :draftId")
    suspend fun getForDraft(draftId: String): List<PendingImageUploadEntity>

    @Query("SELECT * FROM pending_image_uploads WHERE syncStatus = 'PENDING' AND remoteScreeningId IS NOT NULL")
    suspend fun getReadyForUpload(): List<PendingImageUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingImageUploadEntity): Long

    @Query("UPDATE pending_image_uploads SET remoteScreeningId = :screeningId WHERE draftId = :draftId")
    suspend fun attachRemoteScreeningId(draftId: String, screeningId: Int)

    @Query("UPDATE pending_image_uploads SET syncStatus = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("DELETE FROM pending_image_uploads WHERE draftId = :draftId")
    suspend fun deleteForDraft(draftId: String)

    @Query("DELETE FROM pending_image_uploads")
    suspend fun clearAll()
}

@Database(
    entities = [ScreeningDraftEntity::class, PendingImageUploadEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class OralCareDatabase : RoomDatabase() {
    abstract fun screeningDraftDao(): ScreeningDraftDao
    abstract fun pendingImageDao(): PendingImageDao
}
