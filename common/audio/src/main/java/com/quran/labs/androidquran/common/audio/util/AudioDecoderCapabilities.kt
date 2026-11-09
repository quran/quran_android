package com.quran.labs.androidquran.common.audio.util

/**
 * Reports which audio codecs the current device is able to decode.
 *
 * This is used to avoid selecting an audio format that the device has no decoder for. Some
 * devices (notably certain e-ink tablets) ship firmware without an Opus decoder, in which case
 * Opus playback fails silently - the player advances but no sound is produced. Checking for
 * decoder support up front lets us fall back to a format the device can actually play (mp3).
 */
interface AudioDecoderCapabilities {
  /** Returns true if the device has a decoder capable of playing Opus audio. */
  fun isOpusSupported(): Boolean
}
