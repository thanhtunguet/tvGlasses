package info.thanhtunguet.tvglasses

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import java.util.concurrent.atomic.AtomicBoolean

class CameraStreamManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "CameraStreamManager"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        
        @Volatile
        private var INSTANCE: CameraStreamManager? = null
        
        fun getInstance(context: Context): CameraStreamManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CameraStreamManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var player: ExoPlayer? = null
    private var currentConfig: ConfigurationObject? = null
    private var currentUri: Uri? = null
    private var isConnecting = AtomicBoolean(false)
    private var reconnectAttempts = 0
    private var shouldMaintainConnection = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val reconnectRunnable = Runnable { attemptConnection() }
    
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    Log.d(TAG, "Stream connected successfully")
                    reconnectAttempts = 0
                    isConnecting.set(false)
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "Stream ended, attempting reconnection")
                    scheduleReconnect()
                }
            }
        }
        
        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Stream error: ${error.message}", error)
            scheduleReconnect()
        }
    }
    
    fun startMaintainingConnection(config: ConfigurationObject) {
        shouldMaintainConnection = true
        updateConfiguration(config)
    }
    
    fun stopMaintainingConnection() {
        shouldMaintainConnection = false
        handler.removeCallbacks(reconnectRunnable)
        releasePlayer()
    }
    
    fun updateConfiguration(config: ConfigurationObject) {
        val newUri = buildRtspUri(config)
        
        // Check if we need to reconnect due to configuration change
        val needsReconnect = currentConfig?.rtspUrl != config.rtspUrl ||
                currentConfig?.username != config.username ||
                currentConfig?.password != config.password
        
        currentConfig = config
        currentUri = newUri
        
        if (shouldMaintainConnection && newUri != null && needsReconnect) {
            attemptConnection()
        } else if (newUri == null) {
            releasePlayer()
        }
    }
    
    fun attachToPlayerView(playerView: PlayerView) {
        playerView.player = player
        // Ensure we have a connection if we should maintain one
        if (shouldMaintainConnection && currentUri != null && player == null) {
            attemptConnection()
        }
    }
    
    fun detachFromPlayerView(playerView: PlayerView) {
        if (playerView.player == player) {
            playerView.player = null
        }
    }
    
    fun isStreamReady(): Boolean {
        return player?.playbackState == Player.STATE_READY
    }
    
    fun hasValidConfiguration(): Boolean {
        return currentUri != null
    }
    
    private fun attemptConnection() {
        if (isConnecting.get() || currentUri == null || !shouldMaintainConnection) {
            return
        }
        
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnection attempts reached")
            return
        }
        
        isConnecting.set(true)
        reconnectAttempts++
        
        Log.d(TAG, "Attempting to connect to stream (attempt $reconnectAttempts)")
        
        // Release existing player
        releasePlayer()
        
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(currentUri!!)
                .setMimeType(MimeTypes.APPLICATION_RTSP)
                .build()
            
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .setTimeoutMs(10000) // 10 second timeout
                .createMediaSource(mediaItem)
            
            val exoPlayer = ExoPlayer.Builder(context).build()
            exoPlayer.addListener(playerListener)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            
            player = exoPlayer
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create player", e)
            isConnecting.set(false)
            scheduleReconnect()
        }
    }
    
    private fun scheduleReconnect() {
        if (!shouldMaintainConnection) return
        
        isConnecting.set(false)
        handler.removeCallbacks(reconnectRunnable)
        
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            val delay = RECONNECT_DELAY_MS * reconnectAttempts // Exponential backoff
            Log.d(TAG, "Scheduling reconnection in ${delay}ms")
            handler.postDelayed(reconnectRunnable, delay)
        } else {
            Log.w(TAG, "Max reconnection attempts reached, stopping reconnection attempts")
        }
    }
    
    private fun releasePlayer() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
        isConnecting.set(false)
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