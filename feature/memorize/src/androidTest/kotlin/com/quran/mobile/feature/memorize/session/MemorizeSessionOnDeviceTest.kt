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
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.recitation.events.RecitationEventPresenter
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Whole memorization sessions on a real phone, one per clip of clips/manifest.json (staged by
 * tools/memorize/stage_test_clips.py): professional reciters of different styles, non-professional
 * readers and non-native readers. Each clip is played into the session in real time, as the
 * microphone would deliver it, through the real speech engine, tracker and hint scheduler.
 *
 * Reports per clip and per group how much of what was recited is revealed, the mistakes marked and
 * hints given, and how long after a word is said it appears. Results go to logcat (tag
 * MemorizeSession) and to the instrumentation status (e.g. Firebase Test Lab).
 */
@RunWith(AndroidJUnit4::class)
class MemorizeSessionOnDeviceTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  private class Clip(
    val file: String,
    val group: String,
    val label: String,
    val start: SuraAyah,
    val words: Int,
    /** When each of the [words] ends in the clip (its first recitation), when known. */
    val wordEndsMs: List<Long>?,
    /** "complete", "mistake" (a wrong word or ayah should be caught) or "incomplete". */
    val expect: String,
  )

  private class Outcome(val clip: Clip, val revealed: Int, val mistakes: Int, val hints: Int, val latencies: List<Double>) {
    val complete get() = revealed == clip.words
    val asExpected
      get() = when (clip.expect) {
        "complete" -> complete
        "mistake" -> mistakes > 0 || !complete
        else -> !complete
      }
  }

  @Test
  fun followsDifferentReaders() {
    val models = SpeechModelStore(context)
    installModel(models)
    val preferences = MemorizePreferences(context).apply {
      correctorEnabled = true
      hintDelaySeconds = 4
    }
    val source = ClipSource()
    val controller = MemorizeSessionController(
      lexicon = LexiconRepository(context),
      models = models,
      preferences = preferences,
      recitationEventPresenter = RecitationEventPresenter(),
      engineFactory = { SherpaRecitationEngine(it.modelFile, it.tokensFile) },
      promptPlayer = RecordingVoice(),
      audioSources = source,
      callState = { false },
    )

    val outcomes = clips().map { clip -> session(controller, source, clip) }

    val status = Bundle()
    outcomes.forEachIndexed { i, outcome ->
      val line = outcome.describe()
      Log.i(TAG, line)
      status.putString("clip_%02d".format(i + 1), line)
    }
    for ((group, results) in outcomes.groupBy { it.clip.group }) {
      val expectedComplete = results.filter { it.clip.expect == "complete" }
      val words = expectedComplete.sumOf { it.clip.words }
      val revealed = expectedComplete.sumOf { it.revealed }
      val latencies = results.flatMap { it.latencies }.sorted()
      val line = ("group=%s clips=%d as_expected=%d/%d revealed=%d/%d (%.1f%%) mistakes=%d hints=%d " +
        "latency_p50=%.2fs latency_p90=%.2fs late_frames=%d")
        .format(
          group, results.size, results.count { it.asExpected }, results.size, revealed, words,
          100.0 * revealed / maxOf(words, 1), results.sumOf { it.mistakes }, results.sumOf { it.hints },
          latencies.percentile(0.5), latencies.percentile(0.9), source.late.get(),
        )
      Log.i(TAG, line)
      status.putString("group_$group", line)
    }
    instrumentation.sendStatus(0, status)

    // professional recitation is followed almost entirely; the other groups are measured, not judged
    val professional = outcomes.filter { it.clip.group == "professional" }
    assertThat(professional.sumOf { it.revealed }.toDouble() / professional.sumOf { it.clip.words }).isAtLeast(0.9)
  }

  /** Runs one session on [clip] and measures it. */
  private fun session(controller: MemorizeSessionController, source: ClipSource, clip: Clip): Outcome {
    source.next(loadWav(clip.file))
    val revealedAt = HashMap<Int, Long>()
    var mistakes = 0
    var lastMistake: Int? = null
    instrumentation.runOnMainSync { controller.start(clip.start) }
    while (!source.finished) {
      val state = controller.state.value
      if (state != null) {
        val now = SystemClock.elapsedRealtime()
        for (i in 0 until minOf(state.cursor, clip.words)) if (i !in revealedAt) revealedAt[i] = now
        if (state.mistake != null && state.mistake != lastMistake) mistakes++
        lastMistake = state.mistake
      }
      SystemClock.sleep(10)
    }
    val hinted = controller.state.value?.hinted.orEmpty().filter { it < clip.words }.toSet()
    instrumentation.runOnMainSync { controller.stop() }

    val latencies = clip.wordEndsMs?.let { ends ->
      (0 until clip.words).mapNotNull { i ->
        val at = revealedAt[i] ?: return@mapNotNull null
        if (i in hinted) null else (at - (source.startedAt + ends[i])) / 1000.0
      }
    }.orEmpty()
    val revealed = (0 until clip.words).count { it in revealedAt && it !in hinted }
    return Outcome(clip, revealed, mistakes, hinted.size, latencies)
  }

  private fun Outcome.describe(): String {
    val sorted = latencies.sorted()
    val latency = if (sorted.isEmpty()) "" else {
      " latency_p50=%.2fs latency_p90=%.2fs".format(sorted.percentile(0.5), sorted.percentile(0.9))
    }
    return "clip=%s group=%s start=%d:%d revealed=%d/%d mistakes=%d hints=%d expect=%s ok=%b%s label=\"%s\"".format(
      clip.file.substringAfterLast('/'), clip.group, clip.start.sura, clip.start.ayah, revealed, clip.words,
      mistakes, hints, clip.expect, asExpected, latency, clip.label,
    )
  }

  private fun List<Double>.percentile(p: Double): Double =
    if (isEmpty()) Double.NaN else this[((size - 1) * p).roundToInt()]

  private fun clips(): List<Clip> {
    val json = instrumentation.context.assets.open("clips/manifest.json").use { String(it.readBytes()) }
    val list = JSONObject(json).getJSONArray("clips")
    return (0 until list.length()).map { i ->
      val c = list.getJSONObject(i)
      val ends = c.optJSONArray("word_ends_ms")
      Clip(
        file = c.getString("file"),
        group = c.getString("group"),
        label = c.getString("label"),
        start = SuraAyah(c.getInt("sura"), c.getInt("ayah")),
        words = c.getInt("words"),
        wordEndsMs = ends?.let { a -> (0 until a.length()).map { a.getLong(it) } },
        expect = c.getString("expect"),
      )
    }
  }

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

  /**
   * Plays the current clip in 100 ms frames at the pace of a real microphone, from the moment the
   * session starts listening, then 3 s of silence.
   */
  private class ClipSource : AudioSourceFactory {
    @Volatile var finished = false
    @Volatile var startedAt = 0L
    // frames the audio thread itself couldn't deliver on time (the phone was too busy)
    val late = AtomicInteger()
    private var audio = FloatArray(0)

    fun next(clip: FloatArray) {
      audio = clip
      finished = false
    }

    override fun create(onFrame: (FloatArray, Int, Boolean) -> Unit, onError: (Throwable) -> Unit): AudioSource {
      val audio = audio
      return object : AudioSource {
        @Volatile private var running = false

        override fun start() {
          running = true
          Thread({
            val vad = EnergyVoiceActivity()
            val frames = audio.size / FRAME + (TRAILING_SILENCE_MS / 100).toInt()
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
          }, "clip-audio").start()
        }

        override fun stop() {
          running = false
        }
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
    const val TRAILING_SILENCE_MS = 3_000L
  }
}
