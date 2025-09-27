package info.thanhtunguet.tvglasses

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Bundle
import android.widget.ImageButton
import info.thanhtunguet.tvglasses.UriEncodingUtils
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
    private lateinit var cameraStreamManager: CameraStreamManager
    private var currentConfiguration: ConfigurationObject = ConfigurationObject()
    private var skipUnlockOnResume: Boolean = false
    private var isUnlocked: Boolean = false
    private var passwordDialog: AlertDialog? = null
    private var isReturningFromPlayback: Boolean = false
    private var hasRequestedPermissions: Boolean = false
    private var hasPromptedForLauncher: Boolean = false
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            showPermissionDeniedDialog()
        }
    }
    
    private val launcherSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // User returned from launcher settings
        checkIfSetAsDefaultLauncher()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = ConfigurationRepository.create(applicationContext)
        currentConfiguration = repository.loadConfiguration()
        cameraStreamManager = CameraStreamManager.getInstance(this)

        val rtspField = findViewById<TextInputEditText>(R.id.editRtspUrl)
        val usernameField = findViewById<TextInputEditText>(R.id.editUsername)
        val passwordField = findViewById<TextInputEditText>(R.id.editPassword)
        val modeGroup = findViewById<RadioGroup>(R.id.radioModeGroup)
        val openViewButton = findViewById<MaterialButton>(R.id.buttonOpenView)
        val openAppsButton = findViewById<MaterialButton>(R.id.buttonOpenApps)
        val setDefaultLauncherButton = findViewById<MaterialButton>(R.id.buttonSetDefaultLauncher)
        val manageVideosButton = findViewById<MaterialButton>(R.id.buttonManageVideos)
        val systemSettingsButton = findViewById<ImageButton>(R.id.buttonOpenSystemSettings)

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
                
                // Update camera stream manager with new configuration
                updateCameraStream()
                
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
                
                // Update camera stream manager with new configuration
                updateCameraStream()
                
                // Update RTSP URL display with new credentials
                updateUrlDisplay(rtspField)
            }
        }

        passwordField.doAfterTextChanged { text ->
            val updatedPassword = text?.toString().orEmpty()
            if (updatedPassword != currentConfiguration.password) {
                currentConfiguration = currentConfiguration.copy(password = updatedPassword)
                repository.saveConfiguration(currentConfiguration)
                
                // Update camera stream manager with new configuration
                updateCameraStream()
                
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

        openAppsButton.setOnClickListener {
            startActivity(Intent(this, AppDrawerActivity::class.java))
        }

        manageVideosButton.setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }

        setDefaultLauncherButton.setOnClickListener {
            if (isDefaultLauncher()) {
                // Already set as default launcher
                MaterialAlertDialogBuilder(this)
                    .setTitle("Already Default")
                    .setMessage("tvGlasses is already set as your default launcher.")
                    .setPositiveButton("OK", null)
                    .show()
            } else {
                // Open launcher settings
                openLauncherSettings()
            }
        }

        systemSettingsButton.setOnClickListener {
            openSystemSettings()
        }

        // Initialize camera stream if configuration is valid
        updateCameraStream()

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
        
        if (repository.hasAppLockPassword()) {
            promptForPassword()
        } else {
            // No password set, user is automatically unlocked
            isUnlocked = true
            checkAndRequestPermissions()
        }
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
        // Stop camera stream when app is destroyed
        cameraStreamManager.stopMaintainingConnection()
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
            val username = Uri.decode(parts.getOrElse(0) { "" })
            val password = Uri.decode(parts.getOrElse(1) { "" })
            
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

            val encodedUsername = UriEncodingUtils.encodeUserInfoComponent(username)
            val encodedPassword = UriEncodingUtils.encodeUserInfoComponent(password)
            
            val userInfo = buildString {
                append(encodedUsername)
                if (password.isNotEmpty()) {
                    append(":")
                    append(encodedPassword)
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
                        checkAndRequestPermissions()
                    }
                }
            }
        }

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { passwordDialog = null }
        dialog.show()
        
        // Autofocus on the new password field
        newPasswordField.requestFocus()
        
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
                        checkAndRequestPermissions()
                    }
                }
            }
        }

        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { passwordDialog = null }
        dialog.show()
        
        // Autofocus on the password field
        passwordField.requestFocus()
        
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
            PlaybackMode.VIDEO -> PlayerActivity::class.java
        }
        startActivity(Intent(this, destination))
    }
    
    private fun checkAndRequestPermissions() {
        if (hasRequestedPermissions) return
        
        val requiredPermissions = mutableListOf<String>()
        
        // Check storage permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - Use granular media permissions
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_VIDEO) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(android.Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 and below - Use READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (requiredPermissions.isNotEmpty()) {
            hasRequestedPermissions = true
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            onPermissionsGranted()
        }
    }
    
    private fun onPermissionsGranted() {
        // Permissions granted, now check if we should prompt for default launcher
        promptForDefaultLauncher()
        // Also ensure camera stream is started if we have valid configuration
        updateCameraStream()
    }
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("Storage permissions are required for video playback. Please grant permissions in Settings.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Later", null)
            .show()
    }
    
    private fun promptForDefaultLauncher() {
        if (hasPromptedForLauncher) return
        if (isDefaultLauncher()) return
        
        hasPromptedForLauncher = true
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Set as Default Launcher")
            .setMessage("Would you like to set tvGlasses as your default launcher? This will make it the home screen that appears when you press the home button.")
            .setPositiveButton("Set as Default") { _, _ ->
                openLauncherSettings()
            }
            .setNegativeButton("Later", null)
            .show()
    }
    
    private fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }
    
    private fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(appSettingsIntent)
        }
    }
    
    private fun openLauncherSettings() {
        try {
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            launcherSettingsLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback for devices that don't support direct launcher settings
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }
    
    private fun checkIfSetAsDefaultLauncher() {
        if (isDefaultLauncher()) {
            // User has set the app as default launcher
            MaterialAlertDialogBuilder(this)
                .setTitle("Success!")
                .setMessage("tvGlasses is now your default launcher.")
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun updateCameraStream() {
        if (hasValidCameraConfiguration()) {
            cameraStreamManager.updateConfiguration(currentConfiguration)
            if (!cameraStreamManager.hasValidConfiguration()) {
                cameraStreamManager.startMaintainingConnection(currentConfiguration)
            }
        } else {
            cameraStreamManager.stopMaintainingConnection()
        }
    }
    
    private fun hasValidCameraConfiguration(): Boolean {
        return currentConfiguration.rtspUrl.isNotBlank()
    }
    
}
