package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class VaultRepository(private val context: Context, database: AppDatabase) {
    private val dao = database.vaultDao()
    
    val normalNotes: Flow<List<NormalNote>> = dao.getAllNormalNotes()
    val secretPhotos: Flow<List<SecretPhoto>> = dao.getAllSecretPhotos()
    val secretFiles: Flow<List<SecretFile>> = dao.getAllSecretFiles()
    val intruderLogs: Flow<List<IntruderLog>> = dao.getAllIntruderLogs()

    suspend fun insertNormalNote(note: NormalNote) = withContext(Dispatchers.IO) {
        dao.insertNormalNote(note)
    }

    suspend fun deleteNormalNote(note: NormalNote) = withContext(Dispatchers.IO) {
        dao.deleteNormalNote(note)
    }

    suspend fun getSetting(key: String): String? = withContext(Dispatchers.IO) {
        dao.getSetting(key)
    }

    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        dao.insertSetting(VaultSetting(key, value))
    }

    suspend fun importPhoto(uri: Uri, isDecoy: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val (name, _) = getFileNameAndSize(uri)
            val photosDir = File(context.filesDir, "vault/photos").apply { mkdirs() }
            val uniqueName = "${UUID.randomUUID()}_$name"
            val targetFile = File(photosDir, uniqueName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                dao.insertSecretPhoto(
                    SecretPhoto(
                        originalName = name,
                        internalPath = targetFile.absolutePath,
                        sizeBytes = targetFile.length(),
                        isDecoy = isDecoy
                    )
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deletePhoto(photo: SecretPhoto) = withContext(Dispatchers.IO) {
        try {
            val file = File(photo.internalPath)
            if (file.exists()) {
                file.delete()
            }
            dao.deleteSecretPhoto(photo)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun importFile(uri: Uri, isDecoy: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val (name, _) = getFileNameAndSize(uri)
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val filesDir = File(context.filesDir, "vault/files").apply { mkdirs() }
            val uniqueName = "${UUID.randomUUID()}_$name"
            val targetFile = File(filesDir, uniqueName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                dao.insertSecretFile(
                    SecretFile(
                        originalName = name,
                        mimeType = mimeType,
                        sizeBytes = targetFile.length(),
                        internalPath = targetFile.absolutePath,
                        isDecoy = isDecoy
                    )
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFile(file: SecretFile) = withContext(Dispatchers.IO) {
        try {
            val systemFile = File(file.internalPath)
            if (systemFile.exists()) {
                systemFile.delete()
            }
            dao.deleteSecretFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun insertIntruderLog(log: IntruderLog) = withContext(Dispatchers.IO) {
        dao.insertIntruderLog(log)
    }

    suspend fun deleteIntruderLog(log: IntruderLog) = withContext(Dispatchers.IO) {
        try {
            log.imagePath?.let { path ->
                val file = java.io.File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        dao.deleteIntruderLog(log)
    }

    suspend fun clearAllIntruderLogs() = withContext(Dispatchers.IO) {
        try {
            val targetDir = java.io.File(context.filesDir, "intruder_photos")
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        dao.clearAllIntruderLogs()
    }

    private fun getFileNameAndSize(uri: Uri): Pair<String, Long> {
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { name = it }
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(name, size)
    }
}
