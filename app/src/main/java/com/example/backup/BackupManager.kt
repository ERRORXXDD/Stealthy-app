package com.example.backup

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import java.io.*
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {
    private const val TAG = "BackupManager"
    
    // Directories for simulated remote cloud storage
    private fun getCloudDir(context: Context, provider: String): File {
        val providerFolder = provider.lowercase(Locale.US).replace(" ", "_")
        return File(context.filesDir, "remote_cloud/$providerFolder").apply { mkdirs() }
    }

    // Encrypt and save data to simulated cloud folder
    fun performBackup(
        context: Context,
        provider: String,
        passphrase: String, // PIN or Pattern coords
        onSuccess: (Long, String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (passphrase.isEmpty()) {
            onError("Vault credentials are required to secure the backup.")
            return
        }
        
        try {
            // 1. Create temporary zip file containing DB and assets
            val tempZip = File(context.cacheDir, "vault_temp.zip")
            if (tempZip.exists()) tempZip.delete()
            
            zipVault(context, tempZip)
            
            if (!tempZip.exists() || tempZip.length() == 0L) {
                onError("No vault content found to backup.")
                return
            }
            
            // 2. Encrypt the entire zip file
            val zipBytes = tempZip.readBytes()
            tempZip.delete() // Clean up unencrypted temp file
            
            val encryptedBytes = encryptData(zipBytes, passphrase.toCharArray())
            
            // 3. Save to simulated cloud directory
            val cloudDir = getCloudDir(context, provider)
            val backupFile = File(cloudDir, "vault_secure_backup.enc")
            
            FileOutputStream(backupFile).use { fos ->
                fos.write(encryptedBytes)
            }
            
            val formattedSize = formatBytesLength(backupFile.length())
            onSuccess(System.currentTimeMillis(), formattedSize)
            
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed: ${e.message}", e)
            onError("Backup encryption failed: ${e.localizedMessage}")
        }
    }

    // Download, decrypt, and apply backup
    fun performRestore(
        context: Context,
        provider: String,
        passphrase: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (passphrase.isEmpty()) {
            onError("Vault credentials are required to decrypt backup.")
            return
        }
        
        val cloudDir = getCloudDir(context, provider)
        val backupFile = File(cloudDir, "vault_secure_backup.enc")
        
        if (!backupFile.exists() || backupFile.length() == 0L) {
            onError("No backup file exists in your $provider cloud storage.")
            return
        }
        
        try {
            // 1. Read encrypted bytes
            val encryptedBytes = backupFile.readBytes()
            
            // 2. Decrypt encrypted zip payload
            val zipBytes = decryptData(encryptedBytes, passphrase.toCharArray())
            
            // 3. Write temp zip file
            val tempZip = File(context.cacheDir, "vault_restore_temp.zip")
            if (tempZip.exists()) tempZip.delete()
            
            FileOutputStream(tempZip).use { fos ->
                fos.write(zipBytes)
            }
            
            // 4. Extract decrypted zip file into local vaults
            unzipVault(context, tempZip)
            tempZip.delete() // Cleanup
            
            onSuccess()
            
        } catch (e: javax.crypto.AEADBadTagException) {
            onError("Incorrect primary credentials. Decryption failed.")
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed: ${e.message}", e)
            onError("Decryption / Restore failed: ${e.localizedMessage}")
        }
    }

    // Get metadata on the latest backup file in simulated cloud
    fun getLatestBackupInfo(context: Context, provider: String): BackupMetadata? {
        val cloudDir = getCloudDir(context, provider)
        val backupFile = File(cloudDir, "vault_secure_backup.enc")
        if (!backupFile.exists() || backupFile.length() == 0L) return null
        
        val sdf = SimpleDateFormat("MMM dd, yyyy | hh:mm a", Locale.getDefault())
        return BackupMetadata(
            formattedDate = sdf.format(Date(backupFile.lastModified())),
            formattedSize = formatBytesLength(backupFile.length()),
            filePath = backupFile.absolutePath
        )
    }

    // -------------- ENCRYPTION ENGINE --------------
    private fun encryptData(data: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, 1000, 256)
        val tmp = factory.generateSecret(spec)
        val secret = SecretKeySpec(tmp.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(data)
        
        // Output format: Salt (16 bytes) + IV (12 bytes) + Ciphertext
        val result = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, result, 0, salt.size)
        System.arraycopy(iv, 0, result, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, result, salt.size + iv.size, ciphertext.size)
        
        return result
    }

    private fun decryptData(encryptedData: ByteArray, password: CharArray): ByteArray {
        if (encryptedData.size < 16 + 12) {
            throw IllegalArgumentException("Encrypted backup payload is corrupted or too short")
        }
        val salt = ByteArray(16)
        val iv = ByteArray(12)
        val ciphertext = ByteArray(encryptedData.size - 16 - 12)
        
        System.arraycopy(encryptedData, 0, salt, 0, 16)
        System.arraycopy(encryptedData, 16, iv, 0, 12)
        System.arraycopy(encryptedData, 16 + 12, ciphertext, 0, ciphertext.size)
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, 1000, 256)
        val tmp = factory.generateSecret(spec)
        val secret = SecretKeySpec(tmp.encoded, "AES")
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    // -------------- FILE ZIP UTILS --------------
    private fun zipVault(context: Context, zipFile: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            // Add Room databases
            val dbFile = context.getDatabasePath("secure_vault.db")
            if (dbFile.exists()) {
                addFileToZip(zos, dbFile, "database/secure_vault.db")
            }
            val dbShm = context.getDatabasePath("secure_vault.db-shm")
            if (dbShm.exists()) {
                addFileToZip(zos, dbShm, "database/secure_vault.db-shm")
            }
            val dbWal = context.getDatabasePath("secure_vault.db-wal")
            if (dbWal.exists()) {
                addFileToZip(zos, dbWal, "database/secure_vault.db-wal")
            }
            
            // Add Photos
            val photosDir = File(context.filesDir, "vault/photos")
            if (photosDir.exists()) {
                photosDir.listFiles()?.forEach { file ->
                    addFileToZip(zos, file, "photos/${file.name}")
                }
            }
            
            // Add Files
            val filesDir = File(context.filesDir, "vault/files")
            if (filesDir.exists()) {
                filesDir.listFiles()?.forEach { file ->
                    addFileToZip(zos, file, "files/${file.name}")
                }
            }
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryPath: String) {
        if (!file.exists()) return
        val entry = ZipEntry(entryPath)
        zos.putNextEntry(entry)
        file.inputStream().use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    private fun unzipVault(context: Context, zipFile: File) {
        // Safe check-and-clear existing assets before unpacking
        val photosDir = File(context.filesDir, "vault/photos")
        if (photosDir.exists()) photosDir.deleteRecursively()
        val filesDir = File(context.filesDir, "vault/files")
        if (filesDir.exists()) filesDir.deleteRecursively()
        
        photosDir.mkdirs()
        filesDir.mkdirs()
        
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory) {
                    val targetFile = when {
                        name.startsWith("database/") -> {
                            val dbName = name.substringAfter("database/")
                            val dbFile = context.getDatabasePath(dbName)
                            dbFile.parentFile?.mkdirs()
                            dbFile
                        }
                        name.startsWith("photos/") -> {
                            val fileName = name.substringAfter("photos/")
                            File(photosDir, fileName)
                        }
                        name.startsWith("files/") -> {
                            val fileName = name.substringAfter("files/")
                            File(filesDir, fileName)
                        }
                        else -> null
                    }
                    
                    targetFile?.let { file ->
                        FileOutputStream(file).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun formatBytesLength(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.toDouble())).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.toDouble(), digitGroups.toDouble()), units[digitGroups])
    }
}

data class BackupMetadata(
    val formattedDate: String,
    val formattedSize: String,
    val filePath: String
)
