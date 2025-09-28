package info.thanhtunguet.tvglasses

import android.content.Context
import android.media.MediaScannerConnection
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
            Log.d(TAG, "Starting video scan...")
            
            // Log all available storage paths for debugging
            logAvailableStoragePaths()
            
            // Use MediaStore to find video files efficiently
            val mediaStoreVideos = scanUsingMediaStore()
            Log.d(TAG, "Found ${mediaStoreVideos.size} videos from MediaStore")
            videos.addAll(mediaStoreVideos)
            
            // Also scan file system for files that might not be in MediaStore
            val fileSystemVideos = scanFileSystem()
            Log.d(TAG, "Found ${fileSystemVideos.size} videos from file system")
            videos.addAll(fileSystemVideos)
            
            // Remove duplicates using a more robust strategy
            // Group videos by name, size, and dateModified to identify the same file with different paths
            val uniqueVideos = videos
                .groupBy { Triple(it.name, it.size, it.dateModified) }
                .values
                .map { duplicateGroup ->
                    // When duplicates are found, prefer MediaStore entries (those with positive IDs)
                    // as they usually have more accurate metadata
                    duplicateGroup.find { it.id > 0 } ?: duplicateGroup.first()
                }
                .sortedBy { it.name.lowercase() }
            
            Log.d(TAG, "Total unique videos found: ${uniqueVideos.size}")
            
            uniqueVideos
                
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for video files", e)
            emptyList()
        }
    }
    
    private fun logAvailableStoragePaths() {
        try {
            Log.d(TAG, "=== Available Storage Paths ===")
            
            // Log primary external storage
            val externalStorage = Environment.getExternalStorageDirectory()
            Log.d(TAG, "External storage: ${externalStorage.absolutePath} (exists: ${externalStorage.exists()}, readable: ${externalStorage.canRead()})")
            
            // Log Download paths
            val downloadPaths = getDownloadFolderPaths()
            Log.d(TAG, "Download paths found: ${downloadPaths.size}")
            downloadPaths.forEach { Log.d(TAG, "  - $it") }
            
            // Log secondary storage
            val secondaryPaths = getSecondaryStoragePaths()
            Log.d(TAG, "Secondary storage paths found: ${secondaryPaths.size}")
            secondaryPaths.forEach { Log.d(TAG, "  - $it") }
            
            // Log TV paths
            val tvPaths = getAndroidTvStoragePaths()
            Log.d(TAG, "TV storage paths found: ${tvPaths.size}")
            tvPaths.forEach { Log.d(TAG, "  - $it") }
            
            Log.d(TAG, "=== End Storage Paths ===")
        } catch (e: Exception) {
            Log.w(TAG, "Error logging storage paths", e)
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
        
        // More comprehensive selection for different video formats and case variations
        val selection = "${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("%.mp4", "%.MP4", "%.mkv", "%.avi")
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
        
        // Scan common Download folders first (highest priority)
        val downloadPaths = getDownloadFolderPaths()
        for (downloadPath in downloadPaths) {
            val downloadDir = File(downloadPath)
            if (downloadDir.exists() && downloadDir.canRead()) {
                Log.d(TAG, "Scanning Download folder: $downloadPath")
                scanDirectory(downloadDir, videos, scannedPaths)
            }
        }
        
        // Scan SD card (secondary external storage)
        val secondaryStoragePaths = getSecondaryStoragePaths()
        for (storagePath in secondaryStoragePaths) {
            val storageDir = File(storagePath)
            if (storageDir.exists() && storageDir.canRead()) {
                Log.d(TAG, "Scanning secondary storage: $storagePath")
                scanDirectory(storageDir, videos, scannedPaths)
            }
        }
        
        // Scan Android TV specific paths
        val tvPaths = getAndroidTvStoragePaths()
        for (tvPath in tvPaths) {
            val tvDir = File(tvPath)
            if (tvDir.exists() && tvDir.canRead()) {
                Log.d(TAG, "Scanning TV storage: $tvPath")
                scanDirectory(tvDir, videos, scannedPaths)
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
        val lowerName = file.name.lowercase()
        val supportedExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".3gp", ".webm")
        return supportedExtensions.any { lowerName.endsWith(it) }
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
        
        // Include internal storage and SD card paths (more comprehensive for TV)
        val validKeywords = listOf(
            "/storage/emulated",     // Internal storage
            "/storage/sdcard",       // SD card
            "/sdcard",              // Legacy SD card path
            "/mnt/sdcard",          // Legacy SD card path
            "/storage/external_sd",  // External SD card
            "/storage/external",     // TV external storage
            "/storage/self",         // Self storage (Android TV)
            "/storage/legacy",       // Legacy storage
            "/mnt/media_rw",        // Media RW mount (but not USB)
            "/data/media"           // Data media path
        )
        
        // Check if path contains any valid keywords
        val hasValidKeyword = validKeywords.any { lowerPath.contains(it) }
        
        // Also include any /storage/ path that doesn't contain "usb"
        val isGeneralStorage = lowerPath.startsWith("/storage/") && !lowerPath.contains("usb")
        
        // Include Download folders specifically
        val isDownloadFolder = lowerPath.contains("/download")
        
        return hasValidKeyword || isGeneralStorage || isDownloadFolder
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
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        volume.directory?.let { dir ->
                            if (dir.exists() && dir.canRead()) {
                                paths.add(dir.absolutePath)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get storage volumes", e)
        }
        
        return paths.distinct()
    }
    
    private fun getDownloadFolderPaths(): List<String> {
        val downloadPaths = mutableListOf<String>()
        
        // Primary Download folders
        val primaryDownloadPaths = listOf(
            "${Environment.getExternalStorageDirectory()}/Download",
            "${Environment.getExternalStorageDirectory()}/Downloads",
            "/sdcard/Download",
            "/sdcard/Downloads",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Downloads"
        )
        
        for (path in primaryDownloadPaths) {
            val dir = File(path)
            if (dir.exists() && dir.canRead()) {
                downloadPaths.add(path)
                Log.d(TAG, "Found Download folder: $path")
            }
        }
        
        // SD card Download folders
        val secondaryStoragePaths = getSecondaryStoragePaths()
        for (storagePath in secondaryStoragePaths) {
            val downloadDirs = listOf(
                "$storagePath/Download",
                "$storagePath/Downloads"
            )
            for (downloadDir in downloadDirs) {
                val dir = File(downloadDir)
                if (dir.exists() && dir.canRead()) {
                    downloadPaths.add(downloadDir)
                    Log.d(TAG, "Found secondary Download folder: $downloadDir")
                }
            }
        }
        
        return downloadPaths.distinct()
    }
    
    private fun getAndroidTvStoragePaths(): List<String> {
        val tvPaths = mutableListOf<String>()
        
        // Android TV specific storage paths
        val commonTvPaths = listOf(
            "/storage/emulated/legacy",
            "/storage/sdcard0",
            "/storage/sdcard",
            "/mnt/sdcard",
            "/mnt/sdcard0",
            "/storage/emulated/legacy/Download",
            "/storage/emulated/legacy/Downloads",
            "/storage/self/primary",
            "/storage/self/primary/Download",
            "/storage/self/primary/Downloads"
        )
        
        for (path in commonTvPaths) {
            val dir = File(path)
            if (dir.exists() && dir.canRead()) {
                tvPaths.add(path)
                Log.d(TAG, "Found TV storage path: $path")
            }
        }
        
        // Check for additional TV box specific mount points
        val tvMountPoints = listOf(
            "/mnt/media_rw",
            "/storage/external",
            "/storage/external_storage"
        )
        
        for (mountPoint in tvMountPoints) {
            val mountDir = File(mountPoint)
            if (mountDir.exists() && mountDir.canRead()) {
                try {
                    mountDir.listFiles()?.forEach { subDir ->
                        if (subDir.isDirectory && subDir.canRead()) {
                            tvPaths.add(subDir.absolutePath)
                            Log.d(TAG, "Found TV mount point: ${subDir.absolutePath}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error scanning TV mount point: $mountPoint", e)
                }
            }
        }
        
        return tvPaths.distinct()
    }
    
    /**
     * Force a media scan of common video directories to ensure MediaStore is up to date
     */
    fun triggerMediaScan() {
        try {
            val pathsToScan = mutableListOf<String>()
            
            // Add common video directories
            val downloadPaths = getDownloadFolderPaths()
            pathsToScan.addAll(downloadPaths)
            
            val externalStorage = Environment.getExternalStorageDirectory()
            if (externalStorage.exists()) {
                pathsToScan.add(externalStorage.absolutePath)
            }
            
            if (pathsToScan.isNotEmpty()) {
                Log.d(TAG, "Triggering media scan for ${pathsToScan.size} paths")
                MediaScannerConnection.scanFile(
                    context,
                    pathsToScan.toTypedArray(),
                    null
                ) { path, uri ->
                    Log.d(TAG, "Media scan completed for: $path")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error triggering media scan", e)
        }
    }
}