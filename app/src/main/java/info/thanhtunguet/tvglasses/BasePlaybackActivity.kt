package info.thanhtunguet.tvglasses

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

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
        enterImmersiveMode()
        
        // Handle back button press
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToMainActivity()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        configuration = repository.loadConfiguration()
        syncModeIfNeeded()
        enterImmersiveMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && (event == null || event.repeatCount == 0)) {
            navigateToMainActivity()
            return true
        }
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

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("returning_from_playback", true)
        startActivity(intent)
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
