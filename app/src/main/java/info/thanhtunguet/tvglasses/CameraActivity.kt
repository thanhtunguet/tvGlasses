package info.thanhtunguet.tvglasses

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

class CameraActivity : BasePlaybackActivity() {

    override val mode: PlaybackMode = PlaybackMode.CAMERA

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        playerView = findViewById(R.id.playerView)
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

        val streamUri = buildRtspUri(configuration)
        if (streamUri == null) {
            Toast.makeText(this, R.string.camera_missing_stream, Toast.LENGTH_SHORT).show()
            return
        }

        val mediaItem = MediaItem.Builder()
            .setUri(streamUri)
            .setMimeType(MimeTypes.APPLICATION_RTSP)
            .build()

        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(mediaItem)

        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setMediaSource(mediaSource)
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

    private fun buildRtspUri(config: ConfigurationObject): Uri? {
        val rawUrl = config.rtspUrl.trim()
        if (rawUrl.isEmpty()) return null

        var uri = Uri.parse(rawUrl)
        if (uri.scheme.isNullOrBlank()) {
            uri = Uri.parse("rtsp://$rawUrl")
        }

        val hasProvidedCredentials = config.username.isNotBlank() || config.password.isNotBlank()
        val userInfoAlreadyPresent = !uri.userInfo.isNullOrBlank()
        if (hasProvidedCredentials && !userInfoAlreadyPresent) {
            val authority = uri.authority ?: return uri
            val sanitizedAuthority = authority.substringAfter('@')
            val userInfo = buildString {
                append(config.username)
                if (config.password.isNotEmpty()) {
                    append(":")
                    append(config.password)
                }
            }
            uri = uri.buildUpon()
                .encodedAuthority("$userInfo@$sanitizedAuthority")
                .build()
        }

        return uri
    }
}
