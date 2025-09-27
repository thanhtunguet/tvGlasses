package info.thanhtunguet.tvglasses

/**
 * Represents the app's runtime configuration, pairing the RTSP camera source with the active playback mode.
 */
data class ConfigurationObject(
    val rtspUrl: String = "",
    val mode: PlaybackMode = PlaybackMode.CAMERA
)

/** Distinguishes between the available playback experiences the glasses can present. */
enum class PlaybackMode {
    CAMERA,
    VIDEO
}
