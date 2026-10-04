package org.quran.app.data

/** MPEG Layer III frame boundaries, not audio identity or recitation authenticity. */
internal object Mp3Validation {
    private val sampleRates = intArrayOf(44100, 48000, 32000)
    private val mpeg1Bitrates = intArrayOf(0,32,40,48,56,64,80,96,112,128,160,192,224,256,320)
    private val mpeg2Bitrates = intArrayOf(0,8,16,24,32,40,48,56,64,80,96,112,128,144,160)

    fun isValid(bytes: ByteArray): Boolean {
        if (bytes.size !in 8..MAX_RECITATION_BYTES) return false
        var end = bytes.size
        // The only accepted trailing metadata is the fixed-length ID3v1 tag.
        if (end >= 128 && bytes[end - 128] == 84.toByte() && bytes[end - 127] == 65.toByte() && bytes[end - 126] == 71.toByte()) end -= 128
        var offset = 0
        if (bytes.size >= 10 && bytes[0] == 73.toByte() && bytes[1] == 68.toByte() && bytes[2] == 51.toByte()) {
            if (unsigned(bytes[3]) !in 2..4 || (6..9).any { unsigned(bytes[it]) >= 128 }) return false
            val length = (unsigned(bytes[6]) shl 21) or (unsigned(bytes[7]) shl 14) or
                (unsigned(bytes[8]) shl 7) or unsigned(bytes[9])
            val footer = if (unsigned(bytes[3]) == 4 && (unsigned(bytes[5]) and 0x10) != 0) 10 else 0
            offset = 10 + length + footer
        }
        if (offset > end - 8) return false
        var frames = 0
        var firstFormat = -1
        while (offset < end) {
            val length = frameLength(bytes, offset) ?: return false
            if (length > end - offset) return false
            // Bitrate may vary, but MPEG version and sample rate must remain stable.
            val format = ((unsigned(bytes[offset + 1]) shr 3) and 3) * 4 + ((unsigned(bytes[offset + 2]) shr 2) and 3)
            if (firstFormat == -1) firstFormat = format else if (format != firstFormat) return false
            offset += length
            frames++
        }
        return offset == end && frames >= 2
    }

    private fun frameLength(bytes: ByteArray, offset: Int): Int? {
        if (offset < 0 || offset > bytes.size - 4) return null
        val first = unsigned(bytes[offset]); val second = unsigned(bytes[offset + 1]); val third = unsigned(bytes[offset + 2])
        if (first != 255 || (second and 0xe0) != 0xe0) return null
        val version = (second shr 3) and 3
        if (version == 1 || ((second shr 1) and 3) != 1) return null
        val rateIndex = (third shr 2) and 3
        val bitrateIndex = (third shr 4) and 15
        if (rateIndex == 3 || bitrateIndex !in 1..14) return null
        val rate = sampleRates[rateIndex] / when (version) { 3 -> 1; 2 -> 2; else -> 4 }
        val bitrates = if (version == 3) mpeg1Bitrates else mpeg2Bitrates
        return (if (version == 3) 144000 else 72000) * bitrates[bitrateIndex] / rate + ((third shr 1) and 1)
    }
    private fun unsigned(byte: Byte) = byte.toInt() and 255
}
