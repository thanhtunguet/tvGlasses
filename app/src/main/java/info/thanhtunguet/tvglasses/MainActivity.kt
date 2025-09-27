package info.thanhtunguet.tvglasses

import android.content.Intent
import android.os.Bundle
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var repository: ConfigurationRepository
    private var currentConfiguration: ConfigurationObject = ConfigurationObject()

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
