package info.thanhtunguet.tvglasses

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class VideoFile(
    val id: Long,
    val path: String,
    val name: String,
    val duration: Long,
    val size: Long,
    val dateModified: Long,
    val thumbnail: String? = null
) {
    val durationFormatted: String
        get() = formatDuration(duration)
    
    val sizeFormatted: String
        get() = formatFileSize(size)
    
    val dateFormatted: String
        get() = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dateModified * 1000))
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
            sizeBytes >= 1024 * 1024 -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            sizeBytes >= 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
    }
}

class VideoFileScanner(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoFileScanner"
    }
    
    suspend fun scanForVideoFiles(): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        try {
            // Use MediaStore to find video files efficiently
            videos.addAll(scanUsingMediaStore())
            
            // Also scan file system for files that might not be in MediaStore
            videos.addAll(scanFileSystem())
            
            // Remove duplicates and sort by name
            videos.distinctBy { it.path }
                .sortedBy { it.name.lowercase() }
                
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for video files", e)
            emptyList()
        }
    }
    
    private suspend fun scanUsingMediaStore(): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )
        
        val selection = "${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%.mp4", "%.MP4")
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"
        
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val path = cursor.getString(pathColumn)
                    val name = cursor.getString(nameColumn)
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    
                    // Filter out USB storage paths and ensure file exists
                    if (isValidStoragePath(path) && File(path).exists()) {
                        videos.add(
                            VideoFile(
                                id = id,
                                path = path,
                                name = name,
                                duration = duration,
                                size = size,
                                dateModified = dateModified
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
        }
        
        videos
    }
    
    private suspend fun scanFileSystem(): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        val scannedPaths = mutableSetOf<String>()
        
        // Scan internal storage
        val internalStorage = Environment.getExternalStorageDirectory()
        if (internalStorage.exists() && internalStorage.canRead()) {
            scanDirectory(internalStorage, videos, scannedPaths)
        }
        
        // Scan SD card (secondary external storage)
        val secondaryStoragePaths = getSecondaryStoragePaths()
        for (storagePath in secondaryStoragePaths) {
            val storageDir = File(storagePath)
            if (storageDir.exists() && storageDir.canRead()) {
                scanDirectory(storageDir, videos, scannedPaths)
            }
        }
        
        videos
    }
    
    private fun scanDirectory(directory: File, videos: MutableList<VideoFile>, scannedPaths: MutableSet<String>) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory && !isSystemDirectory(file)) {
                    scanDirectory(file, videos, scannedPaths)
                } else if (file.isFile && isMP4File(file) && isValidStoragePath(file.absolutePath)) {
                    val path = file.absolutePath
                    if (!scannedPaths.contains(path)) {
                        scannedPaths.add(path)
                        videos.add(
                            VideoFile(
                                id = path.hashCode().toLong(),
                                path = path,
                                name = file.name,
                                duration = 0, // Duration unknown from file system scan
                                size = file.length(),
                                dateModified = file.lastModified() / 1000
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning directory: ${directory.absolutePath}", e)
        }
    }
    
    private fun isMP4File(file: File): Boolean {
        return file.name.lowercase().endsWith(".mp4")
    }
    
    private fun isSystemDirectory(file: File): Boolean {
        val systemDirs = setOf("android", "data", "obb", ".android_secure", "lost+found")
        return systemDirs.any { file.name.lowercase().startsWith(it) }
    }
    
    private fun isValidStoragePath(path: String): Boolean {
        val lowerPath = path.lowercase()
        
        // Exclude USB storage paths (common USB mount points)
        val usbKeywords = listOf("/usb", "/mnt/usb", "/storage/usb", "/mnt/media_rw/usb")
        if (usbKeywords.any { lowerPath.contains(it) }) {
            return false
        }
        
        // Include internal storage and SD card paths
        val validKeywords = listOf(
            "/storage/emulated",  // Internal storage
            "/storage/sdcard",    // SD card
            "/sdcard",            // Legacy SD card path
            "/mnt/sdcard",        // Legacy SD card path
            "/storage/external_sd" // External SD card
        )
        
        return validKeywords.any { lowerPath.contains(it) } || 
               lowerPath.startsWith("/storage/") && !lowerPath.contains("usb")
    }
    
    private fun getSecondaryStoragePaths(): List<String> {
        val paths = mutableListOf<String>()
        
        // Common SD card mount points
        val commonPaths = listOf(
            "/storage/external_sd",
            "/storage/sdcard1",
            "/storage/extSdCard",
            "/mnt/external_sd",
            "/mnt/sdcard2"
        )
        
        for (path in commonPaths) {
            val dir = File(path)
            if (dir.exists() && dir.canRead()) {
                paths.add(path)
            }
        }
        
        // Try to get secondary storage from system
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
            val storageVolumes = storageManager.storageVolumes
            
            for (volume in storageVolumes) {
                if (volume.isRemovable && !volume.isPrimary) {
                    volume.directory?.let { dir ->
                        if (dir.exists() && dir.canRead()) {
                            paths.add(dir.absolutePath)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get storage volumes", e)
        }
        
        return paths.distinct()
    }
}