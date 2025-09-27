package info.thanhtunguet.tvglasses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class VideoActivity : BasePlaybackActivity() {

    override val mode: PlaybackMode = PlaybackMode.VIDEO

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textVideoCount: TextView
    private lateinit var layoutEmptyState: View
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var videoScanner: VideoFileScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
        
        initializeViews()
        setupRecyclerView()
        setupVideoScanner()
        scanForVideos()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewVideos)
        progressBar = findViewById(R.id.progressBarScanning)
        textVideoCount = findViewById(R.id.textVideoCount)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
    }

    private fun setupRecyclerView() {
        videoAdapter = VideoAdapter(
            onVideoClick = { videoFile ->
                playVideo(videoFile)
            },
            coroutineScope = lifecycleScope
        )
        
        recyclerView.apply {
            adapter = videoAdapter
            layoutManager = LinearLayoutManager(this@VideoActivity)
            setHasFixedSize(true)
        }
    }

    private fun setupVideoScanner() {
        videoScanner = VideoFileScanner(this)
    }

    private fun scanForVideos() {
        showScanningState()
        
        lifecycleScope.launch {
            try {
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
        textVideoCount.text = "Scanning for videos..."
        recyclerView.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE
    }

    private fun showVideoList(videos: List<VideoFile>) {
        progressBar.visibility = View.GONE
        textVideoCount.text = "${videos.size} video${if (videos.size != 1) "s" else ""} found"
        recyclerView.visibility = View.VISIBLE
        layoutEmptyState.visibility = View.GONE
        
        videoAdapter.submitList(videos)
    }

    private fun showEmptyState() {
        progressBar.visibility = View.GONE
        textVideoCount.text = "No videos found"
        recyclerView.visibility = View.GONE
        layoutEmptyState.visibility = View.VISIBLE
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
}
