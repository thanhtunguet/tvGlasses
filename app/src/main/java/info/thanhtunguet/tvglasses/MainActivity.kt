package info.thanhtunguet.tvglasses

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = ConfigurationRepository.create(applicationContext)
        currentConfiguration = repository.loadConfiguration()

        val rtspField = findViewById<TextInputEditText>(R.id.editRtspUrl)
        val usernameField = findViewById<TextInputEditText>(R.id.editUsername)
        val passwordField = findViewById<TextInputEditText>(R.id.editPassword)
        val modeGroup = findViewById<RadioGroup>(R.id.radioModeGroup)
        val cameraButton = findViewById<MaterialButton>(R.id.buttonOpenCamera)
        val videoButton = findViewById<MaterialButton>(R.id.buttonOpenVideo)

        rtspField.setText(currentConfiguration.rtspUrl)
        usernameField.setText(currentConfiguration.username)
        passwordField.setText(currentConfiguration.password)

        when (currentConfiguration.mode) {
            PlaybackMode.CAMERA -> modeGroup.check(R.id.radioCameraMode)
            PlaybackMode.VIDEO -> modeGroup.check(R.id.radioVideoMode)
        }

        rtspField.doAfterTextChanged { text ->
            val updatedUrl = text?.toString().orEmpty()
            if (updatedUrl != currentConfiguration.rtspUrl) {
                currentConfiguration = currentConfiguration.copy(rtspUrl = updatedUrl)
                repository.saveConfiguration(currentConfiguration)
            }
        }

        usernameField.doAfterTextChanged { text ->
            val updatedUsername = text?.toString().orEmpty()
            if (updatedUsername != currentConfiguration.username) {
                currentConfiguration = currentConfiguration.copy(username = updatedUsername)
                repository.saveConfiguration(currentConfiguration)
            }
        }

        passwordField.doAfterTextChanged { text ->
            val updatedPassword = text?.toString().orEmpty()
            if (updatedPassword != currentConfiguration.password) {
                currentConfiguration = currentConfiguration.copy(password = updatedPassword)
                repository.saveConfiguration(currentConfiguration)
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

        cameraButton.setOnClickListener {
            openPlayback(PlaybackMode.CAMERA, modeGroup)
        }

        videoButton.setOnClickListener {
            openPlayback(PlaybackMode.VIDEO, modeGroup)
        }

        if (savedInstanceState == null) {
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

    private fun maybeAutoLaunchPlayback(modeGroup: RadioGroup) {
        if (!hasValidConfiguration()) {
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
