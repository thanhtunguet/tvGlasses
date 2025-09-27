package info.thanhtunguet.tvglasses

private const val USER_INFO_SAFE_CHARS = "-._~!$&'()*+,;=:"
private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

/**
 * Percent-encodes user info components so reserved characters like '@' do not break URI parsing.
 */
object UriEncodingUtils {
    fun encodeUserInfoComponent(value: String): String {
        if (value.isEmpty()) return value

        val builder = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val charCount = Character.charCount(codePoint)
            val ch = codePoint.toChar()
            if (codePoint <= 0x7F && (ch.isLetterOrDigit() || USER_INFO_SAFE_CHARS.indexOf(ch) >= 0)) {
                builder.append(ch)
            } else {
                val encodedBytes = String(Character.toChars(codePoint)).encodeToByteArray()
                encodedBytes.forEach { byte ->
                    val b = byte.toInt() and 0xFF
                    builder.append('%')
                    builder.append(HEX_CHARS[b shr 4])
                    builder.append(HEX_CHARS[b and 0x0F])
                }
            }
            index += charCount
        }
        return builder.toString()
    }
}
