package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "normal_notes")
data class NormalNote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "secret_photos")
data class SecretPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalName: String,
    val internalPath: String,
    val sizeBytes: Long,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isDecoy: Boolean = false
)

@Entity(tableName = "secret_files")
data class SecretFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val internalPath: String,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val isDecoy: Boolean = false
)

@Entity(tableName = "vault_settings")
data class VaultSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "intruder_logs")
data class IntruderLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val imagePath: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val attemptType: String,
    val enteredValue: String
)

@Dao
interface VaultDao {
    // Normal Notes
    @Query("SELECT * FROM normal_notes ORDER BY timestamp DESC")
    fun getAllNormalNotes(): Flow<List<NormalNote>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNormalNote(note: NormalNote)

    @Delete
    suspend fun deleteNormalNote(note: NormalNote)

    // Secret Photos
    @Query("SELECT * FROM secret_photos WHERE isDecoy = :isDecoy ORDER BY addedTimestamp DESC")
    fun getSecretPhotos(isDecoy: Boolean): Flow<List<SecretPhoto>>

    @Query("SELECT * FROM secret_photos ORDER BY addedTimestamp DESC")
    fun getAllSecretPhotos(): Flow<List<SecretPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecretPhoto(photo: SecretPhoto)

    @Delete
    suspend fun deleteSecretPhoto(photo: SecretPhoto)

    // Secret Files
    @Query("SELECT * FROM secret_files WHERE isDecoy = :isDecoy ORDER BY addedTimestamp DESC")
    fun getSecretFiles(isDecoy: Boolean): Flow<List<SecretFile>>

    @Query("SELECT * FROM secret_files ORDER BY addedTimestamp DESC")
    fun getAllSecretFiles(): Flow<List<SecretFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecretFile(file: SecretFile)

    @Delete
    suspend fun deleteSecretFile(file: SecretFile)

    // Settings
    @Query("SELECT value FROM vault_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: VaultSetting)

    // Intruder Logs
    @Query("SELECT * FROM intruder_logs ORDER BY timestamp DESC")
    fun getAllIntruderLogs(): Flow<List<IntruderLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntruderLog(log: IntruderLog)

    @Delete
    suspend fun deleteIntruderLog(log: IntruderLog)

    @Query("DELETE FROM intruder_logs")
    suspend fun clearAllIntruderLogs()
}

@Database(
    entities = [NormalNote::class, SecretPhoto::class, SecretFile::class, VaultSetting::class, IntruderLog::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
