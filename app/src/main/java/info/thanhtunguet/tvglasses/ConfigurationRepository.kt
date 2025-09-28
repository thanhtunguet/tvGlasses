package info.thanhtunguet.tvglasses

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val PREFERENCES_NAME = "tv_glasses_configuration"
private const val KEY_RTSP_URL = "rtsp_url"
private const val KEY_USERNAME = "username"
private const val KEY_PASSWORD = "password"
private const val KEY_MODE = "mode"
private const val KEY_APP_LOCK_PASSWORD = "app_lock_password"
private const val KEY_MAIN_LOCK_REQUIRED = "main_lock_required"

/**
 * Persists and retrieves the [ConfigurationObject] backing the app's camera and video playback experience.
 */
class ConfigurationRepository(private val sharedPreferences: SharedPreferences) {

    fun loadConfiguration(): ConfigurationObject {
        val storedMode = sharedPreferences.getString(KEY_MODE, PlaybackMode.CAMERA.name)
        val playbackMode = storedMode?.let { runCatching { PlaybackMode.valueOf(it) }.getOrNull() }
            ?: PlaybackMode.CAMERA

        return ConfigurationObject(
            rtspUrl = sharedPreferences.getString(KEY_RTSP_URL, "") ?: "",
            username = sharedPreferences.getString(KEY_USERNAME, "") ?: "",
            password = sharedPreferences.getString(KEY_PASSWORD, "") ?: "",
            mode = playbackMode
        )
    }

    fun saveConfiguration(configuration: ConfigurationObject) {
        sharedPreferences.edit {
            putString(KEY_RTSP_URL, configuration.rtspUrl)
            putString(KEY_USERNAME, configuration.username)
            putString(KEY_PASSWORD, configuration.password)
            putString(KEY_MODE, configuration.mode.name)
        }
    }

    fun updateRtspUrl(rtspUrl: String) {
        sharedPreferences.edit {
            putString(KEY_RTSP_URL, rtspUrl)
        }
    }

    fun updateUsername(username: String) {
        sharedPreferences.edit {
            putString(KEY_USERNAME, username)
        }
    }

    fun updatePassword(password: String) {
        sharedPreferences.edit {
            putString(KEY_PASSWORD, password)
        }
    }

    fun updatePlaybackMode(mode: PlaybackMode) {
        sharedPreferences.edit {
            putString(KEY_MODE, mode.name)
        }
    }

    fun setAppLockPassword(password: String) {
        sharedPreferences.edit {
            putString(KEY_APP_LOCK_PASSWORD, password)
        }
    }

    fun getAppLockPassword(): String? {
        return sharedPreferences.getString(KEY_APP_LOCK_PASSWORD, null)?.takeIf { it.isNotBlank() }
    }

    fun hasAppLockPassword(): Boolean = !getAppLockPassword().isNullOrEmpty()

    fun validateAppLockPassword(candidate: String): Boolean {
        val stored = getAppLockPassword()
        return stored != null && stored == candidate
    }

    fun isMainLockRequired(): Boolean {
        val defaultValue = hasAppLockPassword()
        return sharedPreferences.getBoolean(KEY_MAIN_LOCK_REQUIRED, defaultValue)
    }

    fun setMainLockRequired(required: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_MAIN_LOCK_REQUIRED, required)
        }
    }

    companion object {
        fun create(context: Context): ConfigurationRepository {
            val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            return ConfigurationRepository(prefs)
        }
    }
}
