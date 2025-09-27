package info.thanhtunguet.tvglasses

import android.os.Bundle

class CameraActivity : BasePlaybackActivity() {

    override val mode: PlaybackMode = PlaybackMode.CAMERA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
    }
}
