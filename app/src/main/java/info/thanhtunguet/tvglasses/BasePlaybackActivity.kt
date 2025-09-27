package info.thanhtunguet.tvglasses

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity

/**
 * Provides common behaviour for playback screens, including handling of hardware key events
 * to toggle between camera and video modes.
 */
abstract class BasePlaybackActivity : AppCompatActivity() {

    protected lateinit var repository: ConfigurationRepository
    protected var configuration: ConfigurationObject = ConfigurationObject()

    /** Defines which playback mode this activity represents. */
    abstract val mode: PlaybackMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ConfigurationRepository.create(applicationContext)
        configuration = repository.loadConfiguration()
        syncModeIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        configuration = repository.loadConfiguration()
        syncModeIfNeeded()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isModeSwitchKey(keyCode) && (event == null || event.repeatCount == 0)) {
            val nextMode = when (mode) {
                PlaybackMode.CAMERA -> PlaybackMode.VIDEO
                PlaybackMode.VIDEO -> PlaybackMode.CAMERA
            }
            navigateTo(nextMode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun navigateTo(nextMode: PlaybackMode) {
        configuration = configuration.copy(mode = nextMode)
        repository.saveConfiguration(configuration)
        val destination = when (nextMode) {
            PlaybackMode.CAMERA -> CameraActivity::class.java
            PlaybackMode.VIDEO -> VideoActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    private fun syncModeIfNeeded() {
        if (configuration.mode != mode) {
            configuration = configuration.copy(mode = mode)
            repository.saveConfiguration(configuration)
        }
    }

    private fun isModeSwitchKey(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_DPAD_CENTER
    }
}
