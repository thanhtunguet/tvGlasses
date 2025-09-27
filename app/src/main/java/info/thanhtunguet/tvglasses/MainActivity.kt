package info.thanhtunguet.tvglasses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private lateinit var repository: ConfigurationRepository
    private var currentConfiguration: ConfigurationObject = ConfigurationObject()
    private var skipUnlockOnResume: Boolean = false
    private var isUnlocked: Boolean = false
    private var passwordDialog: AlertDialog? = null
    private var isReturningFromPlayback: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = ConfigurationRepository.create(applicationContext)
        currentConfiguration = repository.loadConfiguration()

        val rtspField = findViewById<TextInputEditText>(R.id.editRtspUrl)
        val usernameField = findViewById<TextInputEditText>(R.id.editUsername)
        val passwordField = findViewById<TextInputEditText>(R.id.editPassword)
        val modeGroup = findViewById<RadioGroup>(R.id.radioModeGroup)
        val openViewButton = findViewById<MaterialButton>(R.id.buttonOpenView)

        // Display URL with credentials for better UX
        val displayUrl = buildUrlWithCredentials(
            currentConfiguration.rtspUrl,
            currentConfiguration.username,
            currentConfiguration.password
        )
        rtspField.setText(displayUrl)
        usernameField.setText(currentConfiguration.username)
        passwordField.setText(currentConfiguration.password)

        when (currentConfiguration.mode) {
            PlaybackMode.CAMERA -> modeGroup.check(R.id.radioCameraMode)
            PlaybackMode.VIDEO -> modeGroup.check(R.id.radioVideoMode)
        }

        rtspField.doAfterTextChanged { text ->
            val updatedUrl = text?.toString().orEmpty()
            if (updatedUrl != currentConfiguration.rtspUrl) {
                // Parse credentials from URL and update fields
                val (parsedUsername, parsedPassword) = parseUrlCredentials(updatedUrl)
                val cleanUrl = removeCredentialsFromUrl(updatedUrl)
                
                // Update configuration with clean URL and parsed credentials
                currentConfiguration = currentConfiguration.copy(
                    rtspUrl = cleanUrl,
                    username = parsedUsername,
                    password = parsedPassword
                )
                repository.saveConfiguration(currentConfiguration)
                
                // Update UI fields without triggering their change listeners
                if (usernameField.text?.toString() != parsedUsername) {
                    usernameField.setText(parsedUsername)
                }
                if (passwordField.text?.toString() != parsedPassword) {
                    passwordField.setText(parsedPassword)
                }
            }
        }

        usernameField.doAfterTextChanged { text ->
            val updatedUsername = text?.toString().orEmpty()
            if (updatedUsername != currentConfiguration.username) {
                currentConfiguration = currentConfiguration.copy(username = updatedUsername)
                repository.saveConfiguration(currentConfiguration)
                
                // Update RTSP URL display with new credentials
                updateUrlDisplay(rtspField)
            }
        }

        passwordField.doAfterTextChanged { text ->
            val updatedPassword = text?.toString().orEmpty()
            if (updatedPassword != currentConfiguration.password) {
                currentConfiguration = currentConfiguration.copy(password = updatedPassword)
                repository.saveConfiguration(currentConfiguration)
                
                // Update RTSP URL display with new credentials
                updateUrlDisplay(rtspField)
            }
        }

        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.radioVideoMode -> PlaybackMode.VIDEO
                else -> PlaybackMode.CAMERA
            }
            if (newMode != currentConfiguration.mode) {
                currentConfiguration = currentConfiguration.copy(mode = newMode)
                repository.saveConfiguration(currentConfiguration)
            }
        }

        openViewButton.setOnClickListener {
            openPlayback(currentConfiguration.mode, modeGroup)
        }

        if (savedInstanceState == null) {
            // Check if we're returning from playback activity
            val returningFromPlayback = intent.getBooleanExtra("returning_from_playback", false)
            if (returningFromPlayback) {
                isReturningFromPlayback = true
            }
            maybeAutoLaunchPlayback(modeGroup)
        }
    }

    override fun onResume() {
        super.onResume()
        if (skipUnlockOnResume) {
            skipUnlockOnResume = false
            return
        }
        promptForPassword()
    }

    override fun onPause() {
        super.onPause()
        passwordDialog?.dismiss()
        passwordDialog = null
        if (!isChangingConfigurations) {
            isUnlocked = false
        }
    }

    override fun onDestroy() {
        passwordDialog?.dismiss()
        passwordDialog = null
        super.onDestroy()
    }

    private fun parseUrlCredentials(url: String): Pair<String, String> {
        if (url.isBlank()) return Pair("", "")
        
        try {
            var uri = Uri.parse(url.trim())
            
            // Handle URLs without scheme
            if (uri.scheme.isNullOrBlank()) {
                uri = Uri.parse("rtsp://$url")
            }
            
            val userInfo = uri.userInfo
            if (userInfo.isNullOrBlank()) {
                return Pair("", "")
            }
            
            val parts = userInfo.split(":", limit = 2)
            val username = parts.getOrElse(0) { "" }
            val password = parts.getOrElse(1) { "" }
            
            return Pair(username, password)
        } catch (e: Exception) {
            return Pair("", "")
        }
    }
    
    private fun removeCredentialsFromUrl(url: String): String {
        if (url.isBlank()) return url
        
        try {
            var uri = Uri.parse(url.trim())
            
            // Handle URLs without scheme
            val hadNoScheme = uri.scheme.isNullOrBlank()
            if (hadNoScheme) {
                uri = Uri.parse("rtsp://$url")
            }
            
            if (uri.userInfo.isNullOrBlank()) {
                return url // No credentials to remove
            }
            
            val authority = uri.authority ?: return url
            val sanitizedAuthority = authority.substringAfter('@')
            
            val cleanUri = uri.buildUpon()
                .encodedAuthority(sanitizedAuthority)
                .build()
            
            val result = cleanUri.toString()
            return if (hadNoScheme) {
                result.removePrefix("rtsp://")
            } else {
                result
            }
        } catch (e: Exception) {
            return url
        }
    }
    
    private fun updateUrlDisplay(rtspField: TextInputEditText) {
        val displayUrl = buildUrlWithCredentials(
            currentConfiguration.rtspUrl,
            currentConfiguration.username,
            currentConfiguration.password
        )
        if (rtspField.text?.toString() != displayUrl) {
            rtspField.setText(displayUrl)
        }
    }
    
    private fun buildUrlWithCredentials(baseUrl: String, username: String, password: String): String {
        if (baseUrl.isBlank()) return baseUrl
        if (username.isBlank() && password.isBlank()) return baseUrl
        
        try {
            var uri = Uri.parse(baseUrl.trim())
            
            // Handle URLs without scheme
            val hadNoScheme = uri.scheme.isNullOrBlank()
            if (hadNoScheme) {
                uri = Uri.parse("rtsp://$baseUrl")
            }
            
            val authority = uri.authority ?: return baseUrl
            val sanitizedAuthority = authority.substringAfter('@')
            
            val userInfo = buildString {
                append(username)
                if (password.isNotEmpty()) {
                    append(":")
                    append(password)
                }
            }
            
            val newUri = uri.buildUpon()
                .encodedAuthority("$userInfo@$sanitizedAuthority")
                .build()
            
            val result = newUri.toString()
            return if (hadNoScheme) {
                result.removePrefix("rtsp://")
            } else {
                result
            }
        } catch (e: Exception) {
            return baseUrl
        }
    }

    private fun maybeAutoLaunchPlayback(modeGroup: RadioGroup) {
        if (!hasValidConfiguration()) {
            return
        }
        if (isReturningFromPlayback) {
            isReturningFromPlayback = false
            return
        }
        skipUnlockOnResume = true
        openPlayback(currentConfiguration.mode, modeGroup)
    }

    private fun hasValidConfiguration(): Boolean {
        return when (currentConfiguration.mode) {
            PlaybackMode.CAMERA -> currentConfiguration.rtspUrl.isNotBlank()
            PlaybackMode.VIDEO -> true
        }
    }

    private fun promptForPassword() {
        if (isUnlocked) return
        if (passwordDialog?.isShowing == true) return

        if (repository.hasAppLockPassword()) {
            showEnterPasswordDialog()
        } else {
            showSetPasswordDialog()
        }
    }

    private fun showSetPasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_password_set, null)
        val inputLayoutNew = view.findViewById<TextInputLayout>(R.id.inputLayoutNewPassword)
        val inputLayoutConfirm = view.findViewById<TextInputLayout>(R.id.inputLayoutConfirmPassword)
        val newPasswordField = view.findViewById<TextInputEditText>(R.id.inputNewPassword)
        val confirmPasswordField = view.findViewById<TextInputEditText>(R.id.inputConfirmPassword)

        newPasswordField.doAfterTextChanged { inputLayoutNew.error = null }
        confirmPasswordField.doAfterTextChanged { inputLayoutConfirm.error = null }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.password_set_title)
            .setMessage(R.string.password_set_message)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(R.string.password_dialog_confirm, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val newPassword = newPasswordField.text?.toString().orEmpty()
                val confirmPassword = confirmPasswordField.text?.toString().orEmpty()

                when {
                    newPassword.isBlank() -> inputLayoutNew.error = getString(R.string.password_error_empty)
                    confirmPassword.isBlank() -> inputLayoutConfirm.error = getString(R.string.password_error_empty)
                    newPassword != confirmPassword -> inputLayoutConfirm.error = getString(R.string.password_error_mismatch)
                    else -> {
                        repository.setAppLockPassword(newPassword)
                        isUnlocked = true
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { passwordDialog = null }
        dialog.show()
        passwordDialog = dialog
    }

    private fun showEnterPasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_password_enter, null)
        val inputLayout = view.findViewById<TextInputLayout>(R.id.inputLayoutPassword)
        val passwordField = view.findViewById<TextInputEditText>(R.id.inputPassword)

        passwordField.doAfterTextChanged { inputLayout.error = null }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.password_enter_title)
            .setMessage(R.string.password_enter_message)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(R.string.password_dialog_confirm, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val enteredPassword = passwordField.text?.toString().orEmpty()
                when {
                    enteredPassword.isBlank() -> inputLayout.error = getString(R.string.password_error_empty)
                    !repository.validateAppLockPassword(enteredPassword) -> inputLayout.error = getString(R.string.password_error_incorrect)
                    else -> {
                        isUnlocked = true
                        dialog.dismiss()
                    }
                }
            }
        }

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { passwordDialog = null }
        dialog.show()
        passwordDialog = dialog
    }

    private fun openPlayback(mode: PlaybackMode, modeGroup: RadioGroup) {
        if (currentConfiguration.mode != mode) {
            currentConfiguration = currentConfiguration.copy(mode = mode)
            repository.saveConfiguration(currentConfiguration)
            modeGroup.check(
                when (mode) {
                    PlaybackMode.CAMERA -> R.id.radioCameraMode
                    PlaybackMode.VIDEO -> R.id.radioVideoMode
                }
            )
        } else {
            repository.saveConfiguration(currentConfiguration)
        }

        val destination = when (mode) {
            PlaybackMode.CAMERA -> CameraActivity::class.java
            PlaybackMode.VIDEO -> VideoActivity::class.java
        }
        startActivity(Intent(this, destination))
    }
}
