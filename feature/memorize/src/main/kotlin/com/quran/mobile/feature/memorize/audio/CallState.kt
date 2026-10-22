package com.quran.mobile.feature.memorize.audio

import android.content.Context
import android.media.AudioManager

/** Whether the phone is ringing or in a call (phone or VoIP); replaced by a fake in tests. */
fun interface CallState {
  fun isInCall(): Boolean
}

/**
 * Reads the audio mode. During a call the microphone hears nothing (or the call), so silence isn't
 * the user being stuck, and a hint must not be played over the call.
 */
class AudioModeCallState(context: Context) : CallState {
  private val audioManager = context.getSystemService(AudioManager::class.java)

  override fun isInCall(): Boolean = when (audioManager?.mode) {
    AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION -> true
    else -> false
  }
}
