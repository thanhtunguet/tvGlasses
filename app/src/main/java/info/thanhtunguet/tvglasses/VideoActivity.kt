package info.thanhtunguet.tvglasses

import android.os.Bundle

class VideoActivity : BasePlaybackActivity() {

    override val mode: PlaybackMode = PlaybackMode.VIDEO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)
    }
}
