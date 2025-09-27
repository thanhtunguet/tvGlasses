package info.thanhtunguet.tvglasses

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlayerActivity : BasePlaybackActivity() {

    override val mode: PlaybackMode = PlaybackMode.VIDEO

    private lateinit var playerView: PlayerView
    private lateinit var videoScanner: VideoFileScanner

    private var player: ExoPlayer? = null
    private var playlist: List<VideoFile> = emptyList()
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.playerView)
        videoScanner = VideoFileScanner(this)
    }

    override fun onStart() {
        super.onStart()
        loadAndPlayVideos()
    }

    override fun onStop() {
        super.onStop()
        loadJob?.cancel()
        releasePlayer()
    }

    private fun loadAndPlayVideos() {
        loadJob?.cancel()

        loadJob = lifecycleScope.launch {
            try {
                val videos = try {
                    videoScanner.scanForVideoFiles()
                } catch (exception: Exception) {
                    Toast.makeText(this@PlayerActivity, R.string.video_playlist_error, Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                if (videos.isEmpty()) {
                    Toast.makeText(this@PlayerActivity, R.string.video_playlist_empty, Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                playlist = videos
                initializePlayer()
            } finally {
                loadJob = null
            }
        }
    }

    private fun initializePlayer() {
        if (playlist.isEmpty()) return

        releasePlayer()

        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }

        playlist.forEach { video ->
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(video.name)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(File(video.path)))
                .setMediaMetadata(mediaMetadata)
                .build()

            exoPlayer.addMediaItem(mediaItem)
        }

        exoPlayer.prepare()
        playerView.player = exoPlayer
        player = exoPlayer
    }

    private fun releasePlayer() {
        playerView.player = null
        player?.release()
        player = null
    }
}
