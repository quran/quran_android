package com.quran.mobile.feature.memorize.recording

/**
 * Where a time of the session's audio is in what was recorded. They differ once the recorder has
 * dropped frames (a phone too busy to keep up): the session heard them, the recording doesn't have
 * them. Samples at 16 kHz; not thread-safe (the recorder's thread).
 */
internal class AudioClock {
  /** Samples of the session's audio accounted for so far, received or dropped. */
  var sessionSamples = 0L
    private set

  /** Samples of audio received. */
  var audioSamples = 0L
    private set

  // the dropped stretches, in order: where each starts in the session's audio, and its length
  private val gapStarts = ArrayList<Long>()
  private val gapLengths = ArrayList<Long>()

  /** The session has reached [sessionSample]: whatever wasn't received before it was dropped. */
  fun reach(sessionSample: Long) {
    if (sessionSample <= sessionSamples) return
    gapStarts += sessionSamples
    gapLengths += sessionSample - sessionSamples
    sessionSamples = sessionSample
  }

  /** [samples] more were received, right after what the session reached. */
  fun receive(samples: Int) {
    sessionSamples += samples
    audioSamples += samples
  }

  /** Where [sessionSample] is in the received audio; a time in a dropped stretch is at its end. */
  fun toAudio(sessionSample: Long): Long {
    var dropped = 0L
    for (i in gapStarts.indices) {
      val start = gapStarts[i]
      if (sessionSample <= start) break
      dropped += minOf(gapLengths[i], sessionSample - start)
    }
    return (sessionSample - dropped).coerceIn(0L, audioSamples)
  }
}
