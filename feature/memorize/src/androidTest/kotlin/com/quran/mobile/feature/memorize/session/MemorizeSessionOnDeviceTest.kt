package com.quran.mobile.feature.memorize.session

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.audio.AudioSource
import com.quran.mobile.feature.memorize.audio.AudioSourceFactory
import com.quran.mobile.feature.memorize.audio.EnergyVoiceActivity
import com.quran.mobile.feature.memorize.data.LexiconRepository
import com.quran.mobile.feature.memorize.engine.sherpa.SherpaRecitationEngine
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import com.quran.mobile.feature.memorize.prompt.HintVoice
import com.quran.mobile.feature.memorize.prompt.WordTimings
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.recitation.events.RecitationEventPresenter
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToLong

/**
 * A whole memorization session on a real phone: Husary reciting Al-Mulk 1–5 is played into the
 * session in real time, as the microphone would deliver it, through the real speech engine, tracker
 * and hint scheduler. Measures how much of the passage is revealed, how long after a word is said it
 * appears, and whether the phone keeps up. Results go to logcat (tag MemorizeSession) and to the
 * instrumentation status (e.g. Firebase Test Lab).
 */
@RunWith(AndroidJUnit4::class)
class MemorizeSessionOnDeviceTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  @Test
  fun followsAReciterInRealTime() {
    val models = SpeechModelStore(context)
    installModel(models)
    val preferences = MemorizePreferences(context).apply {
      correctorEnabled = true
      hintDelaySeconds = 4
    }
    val voice = RecordingVoice()
    val audio = loadWav("mulk_1_5_husary.wav")
    val source = PacedAudio(audio, trailingSilenceMs = 3_000)
    val controller = MemorizeSessionController(
      lexicon = LexiconRepository(context),
      models = models,
      preferences = preferences,
      recitationEventPresenter = RecitationEventPresenter(),
      engineFactory = { SherpaRecitationEngine(it.modelFile, it.tokensFile) },
      promptPlayer = voice,
      audioSources = source,
      callState = { false },
    )

    // when each word of the passage was revealed (elapsedRealtime), and every mistake mark
    val revealedAt = ConcurrentHashMap<Int, Long>()
    val mistakes = AtomicInteger()
    val startRequested = SystemClock.elapsedRealtime()
    instrumentation.runOnMainSync { controller.start(SuraAyah(67, 1)) }
    var lastMistake: Int? = null
    var listeningAt = 0L
    while (!source.finished) {
      val state = controller.state.value
      if (state != null) {
        if (listeningAt == 0L && state.status == MemorizeState.Status.LISTENING) {
          listeningAt = SystemClock.elapsedRealtime()
        }
        for (i in 0 until state.cursor) revealedAt.putIfAbsent(i, SystemClock.elapsedRealtime())
        if (state.mistake != null && state.mistake != lastMistake) mistakes.incrementAndGet()
        lastMistake = state.mistake
      }
      SystemClock.sleep(10)
    }
    val passage = controller.state.value!!.passage
    val hinted = controller.state.value!!.hinted
    instrumentation.runOnMainSync { controller.stop() }

    // when each word ends in the clip, from the reciter's word timings bundled with the app
    val timings = WordTimings.fromAsset(context, "memorize/timings/husary_murattal_wbw_v1.db")!!
    val words = passage.words.indices.filter { passage.words[it].id.ayah <= 5 }
    val latencies = words.mapNotNull { i ->
      val revealed = revealedAt[i] ?: return@mapNotNull null
      if (i in hinted) return@mapNotNull null
      val end = timings.span(listOf(passage.words[i].id))!!.endMs - CLIP_START_MS
      (revealed - (source.startedAt + end)) / 1000.0
    }.sorted()
    val revealed = words.count { revealedAt.containsKey(it) }

    val line = ("words=%d revealed=%d hinted=%d mistakes=%d latency_p50=%.2fs latency_p90=%.2fs " +
      "latency_max=%.2fs start_ms=%d late_frames=%d")
      .format(
        words.size, revealed, hinted.size, mistakes.get(),
        latencies.percentile(0.5), latencies.percentile(0.9), latencies.lastOrNull() ?: Double.NaN,
        listeningAt - startRequested, source.late.get(),
      )
    Log.i(TAG, line)
    instrumentation.sendStatus(0, Bundle().apply { putString("session", line) })

    assertThat(revealed.toDouble() / words.size).isAtLeast(0.9)
    assertThat(latencies.percentile(0.9)).isLessThan(3.0)
  }

  private fun List<Double>.percentile(p: Double): Double =
    if (isEmpty()) Double.NaN else this[((size - 1) * p).roundToLong().toInt()]

  /** From the test's assets when the run bundled the model (as in Test Lab), else downloaded. */
  private fun installModel(models: SpeechModelStore) {
    if (models.isInstalled()) return
    val bundled = instrumentation.context.assets.list("").orEmpty().toSet()
    val directory = models.modelFile.parentFile!!.apply { mkdirs() }
    for (file in models.spec.files) {
      if (file.name !in bundled) continue
      instrumentation.context.assets.open(file.name).use { input ->
        File(directory, file.name).outputStream().use { input.copyTo(it) }
      }
    }
    if (models.isInstalled()) return
    models.startDownload()
    val deadline = SystemClock.elapsedRealtime() + 10 * 60_000
    while (models.state.value is SpeechModelStore.State.Downloading && SystemClock.elapsedRealtime() < deadline) {
      SystemClock.sleep(500)
    }
    assertThat(models.state.value).isEqualTo(SpeechModelStore.State.Installed)
  }

  /** 16 kHz mono 16-bit PCM WAV. */
  private fun loadWav(name: String): FloatArray {
    val bytes = instrumentation.context.assets.open(name).use { it.readBytes() }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var offset = 12 // "RIFF" size "WAVE"
    while (offset + 8 <= bytes.size) {
      val id = String(bytes, offset, 4, Charsets.US_ASCII)
      val size = buffer.getInt(offset + 4)
      if (id == "data") {
        val pcm = ByteBuffer.wrap(bytes, offset + 8, size).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(pcm.remaining()) { pcm.get(it) / 32_768f }
      }
      offset += 8 + size + (size and 1)
    }
    error("no audio in $name")
  }

  /** Delivers [audio] in 100 ms frames at the pace of a real microphone, then silence. */
  private class PacedAudio(private val audio: FloatArray, private val trailingSilenceMs: Long) : AudioSourceFactory {
    @Volatile var finished = false
    @Volatile var startedAt = 0L
    // frames the audio thread itself couldn't deliver on time (the phone was too busy)
    val late = AtomicInteger()

    override fun create(onFrame: (FloatArray, Int, Boolean) -> Unit, onError: (Throwable) -> Unit): AudioSource =
      object : AudioSource {
        @Volatile private var running = false

        override fun start() {
          running = true
          Thread({
            val vad = EnergyVoiceActivity()
            val frames = audio.size / FRAME + (trailingSilenceMs / 100).toInt()
            startedAt = SystemClock.elapsedRealtime()
            for (k in 0 until frames) {
              if (!running) break
              // keep to the clock, like a microphone: a slow consumer can't slow the audio down
              val due = startedAt + k * 100L
              val wait = due - SystemClock.elapsedRealtime()
              if (wait > 0) SystemClock.sleep(wait) else if (wait < -100) late.incrementAndGet()
              val frame = FloatArray(FRAME)
              val from = k * FRAME
              if (from < audio.size) System.arraycopy(audio, from, frame, 0, minOf(FRAME, audio.size - from))
              onFrame(frame, FRAME, vad.isSpeech(frame, FRAME))
            }
            finished = true
          }, "paced-audio").start()
        }

        override fun stop() {
          running = false
        }
      }
  }

  /** Records the hints instead of playing them. */
  private class RecordingVoice : HintVoice {
    private class Words(val words: List<WordId>) : HintVoice.Clip

    override fun prepare(qariId: Int, words: List<WordId>): HintVoice.Clip = Words(words)

    override fun play(clip: HintVoice.Clip, onStarted: () -> Unit, onFinished: () -> Unit) {
      onStarted()
      onFinished()
    }

    override fun stop() {}
  }

  private companion object {
    const val TAG = "MemorizeSession"
    const val FRAME = 1_600
    // the clip starts 8.699 s into Husary's 067.mp3 (see the androidTest assets)
    const val CLIP_START_MS = 8_699L
  }
}
