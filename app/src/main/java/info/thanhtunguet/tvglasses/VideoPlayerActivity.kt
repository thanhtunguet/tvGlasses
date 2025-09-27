package info.thanhtunguet.tvglasses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerActivity : BasePlaybackActivity() {

    companion object {
        const val EXTRA_VIDEO_PATH = "video_path"
        const val EXTRA_VIDEO_NAME = "video_name"
    }

    override val mode: PlaybackMode = PlaybackMode.VIDEO

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var videoPath: String? = null
    private var videoName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        
        playerView = findViewById(R.id.playerView)
        
        // Get video path from intent
        videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH)
        videoName = intent.getStringExtra(EXTRA_VIDEO_NAME)
        
        if (videoPath == null) {
            Toast.makeText(this, "No video file specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        releasePlayer()
        super.onStop()
    }

    private fun initializePlayer() {
        if (player != null) return
        
        val videoUri = Uri.parse("file://$videoPath")
        val mediaItem = MediaItem.fromUri(videoUri)
        
        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        
        playerView.player = exoPlayer
        player = exoPlayer
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }
}