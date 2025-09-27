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

    private lateinit var playerView: PlayerView
    private lateinit var cameraStreamManager: CameraStreamManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        playerView = findViewById(R.id.playerView)
        cameraStreamManager = CameraStreamManager.getInstance(this)
    }

    override fun onStart() {
        super.onStart()
        attachToStream()
    }

    override fun onStop() {
        detachFromStream()
        super.onStop()
    }

    private fun attachToStream() {
        // Ensure the stream manager has the latest configuration
        cameraStreamManager.updateConfiguration(configuration)
        
        // Check if we have a valid configuration
        if (!cameraStreamManager.hasValidConfiguration()) {
            Toast.makeText(this, R.string.camera_missing_stream, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Attach the player view to the stream manager
        cameraStreamManager.attachToPlayerView(playerView)
        
        // Show a message if the stream is not ready yet
        if (!cameraStreamManager.isStreamReady()) {
            Toast.makeText(this, "Connecting to camera stream...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun detachFromStream() {
        cameraStreamManager.detachFromPlayerView(playerView)
    }
    
    override fun onConfigurationChanged() {
        // Update the stream manager with the new configuration
        cameraStreamManager.updateConfiguration(configuration)
        
        // Check if we still have a valid configuration
        if (!cameraStreamManager.hasValidConfiguration()) {
            Toast.makeText(this, R.string.camera_missing_stream, Toast.LENGTH_SHORT).show()
        }
    }
}
