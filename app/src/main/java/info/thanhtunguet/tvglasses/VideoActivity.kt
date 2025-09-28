package info.thanhtunguet.tvglasses

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoActivity : BasePlaybackActivity() {

    override val mode: PlaybackMode = PlaybackMode.VIDEO

    private lateinit var recyclerView: RecyclerView
    private lateinit var recyclerViewUsbVideos: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textVideoCount: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var layoutUsbVideos: View
    private lateinit var deleteSelectedButton: MaterialButton
    private lateinit var deleteAllButton: MaterialButton
    private lateinit var buttonSyncFromUsb: MaterialButton
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var usbVideoAdapter: VideoAdapter
    private lateinit var videoScanner: VideoFileScanner
    private lateinit var usbStorageHelper: UsbStorageHelper
    private val selectedVideoPaths = linkedSetOf<String>()
    private val selectedUsbVideoPaths = linkedSetOf<String>()
    private var currentVideos: List<VideoFile> = emptyList()
    private var currentUsbVideos: List<VideoFile> = emptyList()
    private var isUsbConnected = false
    
    private val manageStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                proceedWithPendingDeletion()
            } else {
                Toast.makeText(this, "Storage permission is required to delete videos", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private val writeStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            proceedWithPendingDeletion()
        } else {
            Toast.makeText(this, "Storage permission is required to delete videos", Toast.LENGTH_LONG).show()
        }
    }
    
    private var pendingDeletionVideos: List<VideoFile>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        
        initializeViews()
        setupRecyclerView()
        setupDeleteButtons()
        setupVideoScanner()
        setupUsbStorage()
        checkUsbConnection()
        scanForVideos()
        
        // Add debug logging for TV Box troubleshooting
        if (android.util.Log.isLoggable("VideoActivity", android.util.Log.DEBUG)) {
            logDeviceInfo()
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewVideos)
        recyclerViewUsbVideos = findViewById(R.id.recyclerViewUsbVideos)
        progressBar = findViewById(R.id.progressBarScanning)
        textVideoCount = findViewById(R.id.textVideoCount)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        layoutUsbVideos = findViewById(R.id.layoutUsbVideos)
        deleteSelectedButton = findViewById(R.id.buttonDeleteSelected)
        deleteAllButton = findViewById(R.id.buttonDeleteAll)
        buttonSyncFromUsb = findViewById(R.id.buttonSyncFromUsb)
    }

    private fun setupDeleteButtons() {
        deleteSelectedButton.setOnClickListener { confirmDeleteSelected() }
        deleteAllButton.setOnClickListener { confirmDeleteAll() }
        buttonSyncFromUsb.setOnClickListener { syncAllVideosFromUsb() }
        updateSelectionUi()
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(
            onVideoClick = { videoFile ->
                playVideo(videoFile)
            },
            onSelectionToggle = { videoFile, isSelected ->
                onVideoSelectionChanged(videoFile, isSelected)
            },
            coroutineScope = lifecycleScope
        )
        
        usbVideoAdapter = VideoAdapter(
            onVideoClick = { videoFile ->
                playVideo(videoFile)
            },
            onSelectionToggle = { videoFile, isSelected ->
                onUsbVideoSelectionChanged(videoFile, isSelected)
            },
            coroutineScope = lifecycleScope
        )
        
        recyclerView.apply {
            adapter = videoAdapter
            layoutManager = LinearLayoutManager(this@VideoActivity)
            setHasFixedSize(true)
        }
        
        recyclerViewUsbVideos.apply {
            adapter = usbVideoAdapter
            layoutManager = LinearLayoutManager(this@VideoActivity)
            setHasFixedSize(true)
        }
    }

    private fun setupVideoScanner() {
        videoScanner = VideoFileScanner(this)
    }
    
    private fun setupUsbStorage() {
        usbStorageHelper = UsbStorageHelper(this)
    }
    
    private fun checkUsbConnection() {
        lifecycleScope.launch {
            try {
                val hasUsb = usbStorageHelper.hasUsbStorageConnected()
                updateUsbVisibility(hasUsb)
                if (hasUsb) {
                    scanForUsbVideos()
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoActivity", "Error checking USB connection", e)
            }
        }
    }
    
    private fun updateUsbVisibility(hasUsb: Boolean) {
        isUsbConnected = hasUsb
        layoutUsbVideos.visibility = if (hasUsb) View.VISIBLE else View.GONE
    }
    
    private fun scanForUsbVideos() {
        if (!isUsbConnected) return
        
        lifecycleScope.launch {
            try {
                val usbVideos = usbStorageHelper.scanUsbVideos()
                currentUsbVideos = usbVideos
                usbVideoAdapter.submitList(currentUsbVideos)
                selectedUsbVideoPaths.retainAll(currentUsbVideos.map { it.path })
                updateSelectionUi()
            } catch (e: Exception) {
                android.util.Log.e("VideoActivity", "Error scanning USB videos", e)
            }
        }
    }
    
    private fun onUsbVideoSelectionChanged(videoFile: VideoFile, isSelected: Boolean) {
        if (isSelected) {
            selectedUsbVideoPaths.add(videoFile.path)
        } else {
            selectedUsbVideoPaths.remove(videoFile.path)
        }
        updateSelectionUi()
        usbVideoAdapter.updateSelection(selectedUsbVideoPaths)
    }
    
    private fun syncAllVideosFromUsb() {
        if (currentUsbVideos.isEmpty()) {
            Toast.makeText(this, "No videos found on USB storage", Toast.LENGTH_SHORT).show()
            return
        }
        
        val videosToSync = if (selectedUsbVideoPaths.isNotEmpty()) {
            currentUsbVideos.filter { selectedUsbVideoPaths.contains(it.path) }
        } else {
            currentUsbVideos
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Sync Videos from USB")
            .setMessage("Sync ${videosToSync.size} video(s) from USB to internal storage?")
            .setPositiveButton("Sync") { _, _ ->
                performUsbSync(videosToSync)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performUsbSync(videosToSync: List<VideoFile>) {
        buttonSyncFromUsb.isEnabled = false
        buttonSyncFromUsb.text = "Syncing..."
        
        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0
            
            for (video in videosToSync) {
                try {
                    val success = usbStorageHelper.copyVideoFromUsbToStorage(
                        video.path,
                        StorageType.INTERNAL
                    )
                    if (success) {
                        successCount++
                    } else {
                        failCount++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("VideoActivity", "Error syncing video: ${video.path}", e)
                    failCount++
                }
            }
            
            val message = if (failCount == 0) {
                "Successfully synced $successCount video(s)"
            } else {
                "Synced $successCount video(s), failed $failCount"
            }
            
            Toast.makeText(this@VideoActivity, message, Toast.LENGTH_LONG).show()
            
            buttonSyncFromUsb.isEnabled = true
            buttonSyncFromUsb.text = "Sync All"
            
            // Refresh local videos to show newly synced content
            if (successCount > 0) {
                scanForVideos()
            }
        }
    }

    private fun scanForVideos() {
        showScanningState()
        
        lifecycleScope.launch {
            try {
                // Trigger media scan first to ensure MediaStore is up to date
                videoScanner.triggerMediaScan()
                
                // Check USB connection and scan USB videos if needed
                checkUsbConnection()
                
                val videos = videoScanner.scanForVideoFiles()
                if (videos.isNotEmpty()) {
                    showVideoList(videos)
                } else {
                    showEmptyState()
                }
            } catch (e: Exception) {
                android.util.Log.e("VideoActivity", "Error scanning videos", e)
                showEmptyState()
            }
        }
    }

    private fun showScanningState() {
        progressBar.visibility = View.VISIBLE
        textVideoCount.text = getString(R.string.video_scanning)
        recyclerView.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE
        deleteSelectedButton.isEnabled = false
        deleteAllButton.isEnabled = false
        selectedVideoPaths.clear()
        videoAdapter.updateSelection(emptySet())
        
        if (isUsbConnected) {
            selectedUsbVideoPaths.clear()
            usbVideoAdapter.updateSelection(emptySet())
        }
    }

    private fun showVideoList(videos: List<VideoFile>) {
        progressBar.visibility = View.GONE
        currentVideos = videos.toList()
        recyclerView.visibility = View.VISIBLE
        layoutEmptyState.visibility = View.GONE
        videoAdapter.submitList(currentVideos)
        selectedVideoPaths.retainAll(currentVideos.map { it.path })
        updateVideoCount()
        updateSelectionUi()
    }

    private fun showEmptyState() {
        progressBar.visibility = View.GONE
        currentVideos = emptyList()
        textVideoCount.text = getString(R.string.video_no_results)
        recyclerView.visibility = View.GONE
        layoutEmptyState.visibility = if (!isUsbConnected || currentUsbVideos.isEmpty()) View.VISIBLE else View.GONE
        videoAdapter.submitList(emptyList())
        selectedVideoPaths.clear()
        updateSelectionUi()
    }

    private fun updateVideoCount() {
        val count = currentVideos.size
        textVideoCount.text = resources.getQuantityString(R.plurals.video_count, count, count)
    }

    private fun onVideoSelectionChanged(videoFile: VideoFile, isSelected: Boolean) {
        if (isSelected) {
            selectedVideoPaths.add(videoFile.path)
        } else {
            selectedVideoPaths.remove(videoFile.path)
        }
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val selectedCount = selectedVideoPaths.size
        val totalCount = currentVideos.size
        val selectedUsbCount = selectedUsbVideoPaths.size
        val totalUsbCount = currentUsbVideos.size

        deleteSelectedButton.isEnabled = selectedCount > 0
        deleteAllButton.isEnabled = totalCount > 0

        deleteSelectedButton.text = if (selectedCount > 0) {
            getString(R.string.video_delete_selected_with_count, selectedCount)
        } else {
            getString(R.string.video_delete_selected)
        }

        deleteAllButton.text = if (totalCount > 0) {
            getString(R.string.video_delete_all_with_count, totalCount)
        } else {
            getString(R.string.video_delete_all)
        }

        // Update sync button text
        if (isUsbConnected) {
            buttonSyncFromUsb.text = if (selectedUsbCount > 0) {
                "Sync Selected ($selectedUsbCount)"
            } else if (totalUsbCount > 0) {
                "Sync All ($totalUsbCount)"
            } else {
                "Sync All"
            }
            buttonSyncFromUsb.isEnabled = totalUsbCount > 0
        }

        videoAdapter.updateSelection(selectedVideoPaths)
        if (isUsbConnected) {
            usbVideoAdapter.updateSelection(selectedUsbVideoPaths)
        }
    }

    private fun confirmDeleteSelected() {
        if (selectedVideoPaths.isEmpty()) return

        val selectedVideos = currentVideos.filter { selectedVideoPaths.contains(it.path) }
        if (selectedVideos.isEmpty()) {
            selectedVideoPaths.clear()
            updateSelectionUi()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.video_delete_selected)
            .setMessage(getString(R.string.video_delete_selected_confirmation, selectedVideos.size))
            .setPositiveButton(R.string.video_delete_action) { _, _ ->
                performDeletion(selectedVideos)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        if (currentVideos.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.video_delete_all)
            .setMessage(getString(R.string.video_delete_all_confirmation, currentVideos.size))
            .setPositiveButton(R.string.video_delete_action) { _, _ ->
                performDeletion(currentVideos)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performDeletion(targetVideos: List<VideoFile>) {
        if (!hasStoragePermission()) {
            pendingDeletionVideos = targetVideos
            requestStoragePermission()
            return
        }
        
        deleteSelectedButton.isEnabled = false
        deleteAllButton.isEnabled = false

        lifecycleScope.launch {
            val (deletedPaths, failedPaths) = deleteVideoFiles(targetVideos)

            if (deletedPaths.isNotEmpty()) {
                val count = deletedPaths.size
                val message = resources.getQuantityString(R.plurals.video_delete_success, count, count)
                Toast.makeText(this@VideoActivity, message, Toast.LENGTH_SHORT).show()
            }

            if (failedPaths.isNotEmpty()) {
                Toast.makeText(this@VideoActivity, R.string.video_delete_failed, Toast.LENGTH_SHORT).show()
            }

            if (deletedPaths.isNotEmpty()) {
                currentVideos = currentVideos.filterNot { deletedPaths.contains(it.path) }
                selectedVideoPaths.removeAll(deletedPaths)
                if (currentVideos.isEmpty()) {
                    showEmptyState()
                } else {
                    showVideoList(currentVideos)
                }
            } else {
                updateSelectionUi()
            }
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStoragePermissionLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStoragePermissionLauncher.launch(intent)
            }
        } else {
            writeStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    
    private fun proceedWithPendingDeletion() {
        pendingDeletionVideos?.let { videos ->
            pendingDeletionVideos = null
            performDeletion(videos)
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun deleteVideoFiles(targetVideos: List<VideoFile>): Pair<Set<String>, Set<String>> = withContext(Dispatchers.IO) {
        val deletedPaths = mutableSetOf<String>()
        val failedPaths = mutableSetOf<String>()
        val resolver = applicationContext.contentResolver

        targetVideos.forEach { video ->
            val path = video.path
            var deleted = false

            try {
                val file = File(path)
                if (file.exists()) {
                    deleted = file.delete()
                } else {
                    deleted = true
                }

                if (!deleted && video.id > 0) {
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        video.id
                    )
                    deleted = resolver.delete(uri, null, null) > 0
                }

                if (!deleted) {
                    deleted = resolver.delete(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Video.Media.DATA} = ?",
                        arrayOf(path)
                    ) > 0
                }

                if (deleted) {
                    MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null, null)
                    deletedPaths.add(path)
                } else {
                    failedPaths.add(path)
                }
            } catch (securityException: SecurityException) {
                failedPaths.add(path)
            } catch (throwable: Throwable) {
                failedPaths.add(path)
            }
        }

        deletedPaths to failedPaths
    }

    private fun playVideo(videoFile: VideoFile) {
        try {
            // Use built-in video player as primary option
            val intent = Intent(this, VideoPlayerActivity::class.java).apply {
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_PATH, videoFile.path)
                putExtra(VideoPlayerActivity.EXTRA_VIDEO_NAME, videoFile.name)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("VideoActivity", "Error playing video: ${videoFile.path}", e)
            
            // Fallback: try with external video player
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("file://${videoFile.path}"), "video/mp4")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (fallbackIntent.resolveActivity(packageManager) != null) {
                    startActivity(fallbackIntent)
                } else {
                    android.util.Log.w("VideoActivity", "No video player available")
                }
            } catch (fallbackException: Exception) {
                android.util.Log.e("VideoActivity", "Fallback video player also failed", fallbackException)
            }
        }
    }
    
    override fun onUsbConnected() {
        super.onUsbConnected()
        // Refresh USB connection and scan for videos
        checkUsbConnection()
    }
    
    override fun onUsbDisconnected() {
        super.onUsbDisconnected()
        // Hide USB section and clear USB video list
        updateUsbVisibility(false)
        currentUsbVideos = emptyList()
        selectedUsbVideoPaths.clear()
        usbVideoAdapter.submitList(emptyList())
        updateSelectionUi()
    }
    
    override fun onUsbStateChanged(isConnected: Boolean) {
        super.onUsbStateChanged(isConnected)
        // This handles both connection and disconnection
        updateUsbVisibility(isConnected)
        if (isConnected) {
            scanForUsbVideos()
        }
    }
    
    private fun logDeviceInfo() {
        try {
            android.util.Log.d("VideoActivity", "=== Device Debug Info ===")
            android.util.Log.d("VideoActivity", "Device: ${android.os.Build.DEVICE}")
            android.util.Log.d("VideoActivity", "Model: ${android.os.Build.MODEL}")
            android.util.Log.d("VideoActivity", "Manufacturer: ${android.os.Build.MANUFACTURER}")
            android.util.Log.d("VideoActivity", "SDK: ${android.os.Build.VERSION.SDK_INT}")
            
            // Check if this is a TV device
            val uiModeManager = getSystemService(android.content.Context.UI_MODE_SERVICE) as android.app.UiModeManager
            val currentUiMode = uiModeManager.currentModeType
            android.util.Log.d("VideoActivity", "UI Mode: $currentUiMode (TV=${currentUiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION})")
            
            // Log external storage state
            android.util.Log.d("VideoActivity", "External storage state: ${android.os.Environment.getExternalStorageState()}")
            android.util.Log.d("VideoActivity", "External storage directory: ${android.os.Environment.getExternalStorageDirectory()}")
            
            android.util.Log.d("VideoActivity", "=== End Device Info ===")
        } catch (e: Exception) {
            android.util.Log.w("VideoActivity", "Error logging device info", e)
        }
    }
}
