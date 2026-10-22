package com.quran.mobile.feature.memorize.prompt

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.data.source.PageProvider
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorize.tracker.WordId
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File

/**
 * Plays the words given as a hint in a reciter's voice, from the sura files the user downloaded
 * for listening. Fully offline: if the sura isn't downloaded (or the timings don't match the file)
 * nothing is played and the hint is only shown. The player itself is only used on the main thread.
 */
@SingleIn(AppScope::class)
class PromptPlayer @Inject constructor(
  @param:ApplicationContext private val context: Context,
  private val quranFileManager: QuranFileManager,
  private val pageProvider: PageProvider,
) : HintVoice {
  private var player: ExoPlayer? = null
  // the clip that is playing: only its end is reported
  private var listener: Player.Listener? = null
  private val timings = HashMap<Int, WordTimings?>()

  /** Voices that can have word timings shipped with the app: qari id -> timings asset. */
  private val voices = mapOf(
    HUSARY_MURATTAL to "memorize/timings/husary_murattal_wbw_v1.db",
    HUSARY_MUALLIM to "memorize/timings/husary_muallim_wbw_v1.db",
    HUSARY_MUJAWWAD to "memorize/timings/husary_mujawwad_wbw_v1.db",
  )

  /** Reciters that can give hints: word timings are bundled and the reciter exists in the app. */
  fun availableVoices(): List<Int> {
    val bundled = context.assets.list("memorize/timings").orEmpty().toSet()
    val qaris = pageProvider.getQaris().map { it.id }.toSet()
    return voices.filter { (id, asset) -> asset.substringAfterLast('/') in bundled && id in qaris }.keys.toList()
  }

  fun voiceName(qariId: Int): Int? = pageProvider.getQaris().firstOrNull { it.id == qariId }?.nameResource

  enum class VoiceStatus {
    /** Word timings exist and the sura's audio matches them. */
    READY,
    /** No word timings for this reciter. */
    NO_TIMINGS,
    /** The sura's audio hasn't been downloaded. */
    AUDIO_MISSING,
    /** The downloaded file isn't the recording the timings were made for. */
    AUDIO_MISMATCH,
  }

  /** Reads files: call it off the main thread. */
  fun voiceStatus(qariId: Int, sura: Int): VoiceStatus {
    val timings = timingsFor(qariId) ?: return VoiceStatus.NO_TIMINGS
    val expectedSize = timings.expectedFileSize(sura) ?: return VoiceStatus.NO_TIMINGS
    val file = audioFile(qariId, sura) ?: return VoiceStatus.AUDIO_MISSING
    return if (file.extension == "mp3" && file.length() != expectedSize) VoiceStatus.AUDIO_MISMATCH else VoiceStatus.READY
  }

  override fun prepare(qariId: Int, words: List<WordId>): HintVoice.Clip? = clip(qariId, words)

  override fun play(clip: HintVoice.Clip, onStarted: () -> Unit, onFinished: () -> Unit) {
    stop()
    val audio = clip as? FileClip ?: run {
      onFinished()
      return
    }
    val player = player ?: ExoPlayer.Builder(context).build().also {
      it.setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
        /* handleAudioFocus= */ false
      )
      player = it
    }
    player.setMediaItem(
      MediaItem.Builder()
        .setUri(Uri.fromFile(audio.file))
        .setClippingConfiguration(
          MediaItem.ClippingConfiguration.Builder()
            // a little room on both sides so the word's start and ending are heard completely
            .setStartPositionMs(maxOf(0L, audio.span.startMs - LEAD_MS))
            .setEndPositionMs(audio.span.endMs + TAIL_MS)
            .build()
        )
        .build()
    )
    val listener = object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) finished()
      }

      override fun onPlayerError(error: androidx.media3.common.PlaybackException) = finished()

      private fun finished() {
        player.removeListener(this)
        if (this@PromptPlayer.listener === this) this@PromptPlayer.listener = null
        onFinished()
      }
    }
    this.listener = listener
    player.addListener(listener)
    player.prepare()
    player.play()
    onStarted()
  }

  /** Stops the clip that is playing, if any, without calling its onFinished. */
  override fun stop() {
    listener?.let { player?.removeListener(it) }
    listener = null
    player?.stop()
  }

  fun release() {
    player?.release()
    player = null
  }

  private class FileClip(val file: File, val span: WordTimings.Span) : HintVoice.Clip

  private fun clip(qariId: Int, words: List<WordId>): FileClip? {
    if (words.isEmpty()) return null
    val sura = words.first().sura
    // timings are only valid for the exact recording they were measured on
    if (voiceStatus(qariId, sura) != VoiceStatus.READY) return null
    val file = audioFile(qariId, sura) ?: return null
    val span = timingsFor(qariId)?.span(words) ?: return null
    return FileClip(file, span)
  }

  private fun audioFile(qariId: Int, sura: Int): File? {
    val qari = pageProvider.getQaris().firstOrNull { it.id == qariId } ?: return null
    val directory = quranFileManager.audioFileDirectory() ?: return null
    return listOf("mp3", "opus")
      .map { File(directory, "${qari.path}/%03d.$it".format(sura)) }
      .firstOrNull { it.exists() }
  }

  // called from the session's worker and from the start screen
  @Synchronized
  private fun timingsFor(qariId: Int): WordTimings? {
    if (!timings.containsKey(qariId)) {
      timings[qariId] = voices[qariId]?.let { WordTimings.fromAsset(context, it) }
    }
    return timings[qariId]
  }

  companion object {
    const val HUSARY_MURATTAL = 1
    // see MadaniPageProvider
    const val HUSARY_MUALLIM = 1001
    const val HUSARY_MUJAWWAD = 1002
    private const val LEAD_MS = 60L
    private const val TAIL_MS = 120L
  }
}
