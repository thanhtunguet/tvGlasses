package info.thanhtunguet.tvglasses

import android.content.Context
import android.os.Build
import android.os.storage.StorageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class UsbStorageInfo(
    val path: String,
    val isMounted: Boolean,
    val isReadable: Boolean,
    val freeSpace: Long,
    val totalSpace: Long
)

class UsbStorageHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "UsbStorageHelper"
    }
    
    suspend fun getUsbStoragePaths(): List<UsbStorageInfo> = withContext(Dispatchers.IO) {
        val usbStorages = mutableListOf<UsbStorageInfo>()
        
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val storageVolumes = storageManager.storageVolumes
            
            for (volume in storageVolumes) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory?.let { dir ->
                        val path = dir.absolutePath
                        if (isUsbStoragePath(path)) {
                            usbStorages.add(
                                UsbStorageInfo(
                                    path = path,
                                    isMounted = volume.state == "mounted",
                                    isReadable = dir.exists() && dir.canRead(),
                                    freeSpace = if (dir.exists()) dir.freeSpace else 0L,
                                    totalSpace = if (dir.exists()) dir.totalSpace else 0L
                                )
                            )
                        }
                    }
                }
            }
            
            // Also check common USB mount points
            val commonUsbPaths = listOf(
                "/mnt/usb",
                "/storage/usb",
                "/mnt/media_rw/usb",
                "/storage/usbotg"
            )
            
            for (path in commonUsbPaths) {
                val dir = File(path)
                if (dir.exists() && !usbStorages.any { it.path == path }) {
                    usbStorages.add(
                        UsbStorageInfo(
                            path = path,
                            isMounted = true,
                            isReadable = dir.canRead(),
                            freeSpace = if (dir.canRead()) dir.freeSpace else 0L,
                            totalSpace = if (dir.canRead()) dir.totalSpace else 0L
                        )
                    )
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting USB storage paths", e)
        }
        
        usbStorages.filter { it.isReadable }
    }
    
    suspend fun hasUsbStorageConnected(): Boolean = withContext(Dispatchers.IO) {
        getUsbStoragePaths().isNotEmpty()
    }
    
    suspend fun scanUsbVideos(): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        val usbStorages = getUsbStoragePaths()
        
        for (storage in usbStorages) {
            try {
                val storageDir = File(storage.path)
                if (storageDir.exists() && storageDir.canRead()) {
                    scanDirectoryForVideos(storageDir, videos)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error scanning USB storage: ${storage.path}", e)
            }
        }
        
        videos.distinctBy { it.path }.sortedBy { it.name.lowercase() }
    }
    
    suspend fun copyVideoFromUsbToStorage(
        usbVideoPath: String,
        targetStorageType: StorageType = StorageType.INTERNAL
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(usbVideoPath)
            if (!sourceFile.exists() || !sourceFile.canRead()) {
                Log.e(TAG, "Source USB video not accessible: $usbVideoPath")
                return@withContext false
            }
            
            val targetDir = when (targetStorageType) {
                StorageType.INTERNAL -> File(context.getExternalFilesDir(null), "Videos")
                StorageType.SDCARD -> getExternalSdCardPath()?.let { File(it, "Videos") }
            }
            
            if (targetDir == null || !targetDir.exists() && !targetDir.mkdirs()) {
                Log.e(TAG, "Cannot create target directory: $targetDir")
                return@withContext false
            }
            
            val targetFile = File(targetDir, sourceFile.name)
            
            // Check if file already exists
            if (targetFile.exists()) {
                Log.i(TAG, "Video already exists in target: ${targetFile.absolutePath}")
                return@withContext true
            }
            
            // Copy file
            sourceFile.copyTo(targetFile, overwrite = false)
            
            // Verify copy was successful
            val success = targetFile.exists() && targetFile.length() == sourceFile.length()
            
            if (success) {
                Log.i(TAG, "Successfully copied video from USB to storage: ${targetFile.absolutePath}")
                
                // Trigger media scan so the file appears in MediaStore
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
            } else {
                Log.e(TAG, "Failed to copy video from USB")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error copying video from USB", e)
            false
        }
    }
    
    private fun scanDirectoryForVideos(directory: File, videos: MutableList<VideoFile>) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory && !isSystemDirectory(file)) {
                    scanDirectoryForVideos(file, videos)
                } else if (file.isFile && isMP4File(file)) {
                    videos.add(
                        VideoFile(
                            id = file.absolutePath.hashCode().toLong(),
                            path = file.absolutePath,
                            name = file.name,
                            duration = 0, // Duration unknown from file system scan
                            size = file.length(),
                            dateModified = file.lastModified() / 1000
                        )
                    )
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
    
    private fun isUsbStoragePath(path: String): Boolean {
        val lowerPath = path.lowercase()
        val usbKeywords = listOf("/usb", "/mnt/usb", "/storage/usb", "/mnt/media_rw/usb", "/storage/usbotg")
        return usbKeywords.any { lowerPath.contains(it) }
    }
    
    private fun getExternalSdCardPath(): String? {
        try {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val storageVolumes = storageManager.storageVolumes
            
            for (volume in storageVolumes) {
                if (volume.isRemovable && !volume.isPrimary) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory?.let { dir ->
                            if (dir.exists() && dir.canWrite()) {
                                return dir.absolutePath
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not get SD card path", e)
        }
        return null
    }
}

enum class StorageType {
    INTERNAL,
    SDCARD
}