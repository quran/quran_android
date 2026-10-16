package com.quran.labs.androidquran.common.audio.model.playback

/**
 * The playback speeds offered for audio playback, in ascending order.
 *
 * This is the single source of truth for the speeds shown by the audio bar
 * and by the ayah playback panel, so the two surfaces cannot drift apart.
 */
object PlaybackSpeed {
  val speeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

  const val DEFAULT_SPEED: Float = 1.0f
}
