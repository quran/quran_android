package com.quran.mobile.feature.memorize.check

import android.app.ActivityManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.data.LexiconRepository
import com.quran.mobile.feature.memorize.engine.sherpa.SherpaRecitationEngine
import com.quran.mobile.feature.memorize.model.SpeechModelStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The double-check on a device, as the app runs it:
 * - its model is downloaded as published and converted on the device;
 * - it is loaded in a process of its own;
 * - it hears words of Al-Husary's Al-Mulk 1–5;
 * - and the speech engine hears the same as without it while checks run next to it.
 *
 * The last is the corruption that made the check move to its own process. Results go to logcat
 * (tag MemorizeDoubleCheck) and to the instrumentation status.
 */
@RunWith(AndroidJUnit4::class)
class DoubleCheckOnDeviceTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext

  @Test
  fun checksInItsOwnProcessWithoutDisturbingTheSpeechEngine() {
    val speech = SpeechModelStore(context)
    installSpeechModel(speech)

    // downloaded and converted the way the app does it
    val store = SpeechModelStore.of(context, DoubleCheckModel.SPEC)
    val downloadStarted = SystemClock.elapsedRealtime()
    if (!store.isInstalled()) {
      store.startDownload()
      val deadline = downloadStarted + 15 * 60_000
      while (store.state.value is SpeechModelStore.State.Downloading && SystemClock.elapsedRealtime() < deadline) {
        SystemClock.sleep(500)
      }
    }
    val downloadSeconds = (SystemClock.elapsedRealtime() - downloadStarted) / 1000.0
    assertThat(store.state.value).isEqualTo(SpeechModelStore.State.Installed)

    val model = DoubleCheckModel.forTest(store) { file, tokens -> RemotePieceRecognizer(context, file, tokens) }
    val loadStarted = SystemClock.elapsedRealtime()
    val check = requireNotNull(model.open()) { "the double-check didn't load" }
    val loadSeconds = (SystemClock.elapsedRealtime() - loadStarted) / 1000.0
    val processes = context.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().map { it.processName }
    assertThat(processes).contains("${context.packageName}:doublecheck")

    val audio = loadWav("mulk_1_5_husary.wav")
    val ends = wordEnds()
    val lexicon = LexiconRepository(context)
    val mulk = runBlocking { lexicon.passage(SuraAyah(67, 1)) }
    val ikhlas = runBlocking { lexicon.passage(SuraAyah(112, 1)) }

    // the speech engine on its own
    val alone = recognize(speech, audio, check = null)

    // checks: words said in the last seconds are found, a word from elsewhere isn't
    val said = listOf(5, 12, 20, 33, 47).associateWith { k -> check.said(lastSeconds(audio, ends[k] + 300), mulk, k) }
    val elsewhere = check.said(lastSeconds(audio, ends[20] + 300), ikhlas, 2)
    val checkMs = check.lastMs

    // the speech engine again, with a check every 10 s of audio next to it, as in a session
    val withChecks = recognize(speech, audio, check = { heard -> check.said(heard, mulk, 0) })
    check.close()

    val status = Bundle().apply {
      putString("download", "%.1f s, %d MB".format(downloadSeconds, DoubleCheckModel.SPEC.downloadSize / 1_000_000))
      putString("load", "%.1f s".format(loadSeconds))
      putString("said", said.entries.joinToString { "${it.key}=${it.value}" } + " elsewhere=$elsewhere")
      putString("check_ms", checkMs.toString())
      putString("engine", "alone=${alone.length} letters, with checks=${withChecks.length}, same=${alone == withChecks}")
    }
    for (key in status.keySet()) Log.i(TAG, "$key: ${status.getString(key)}")
    instrumentation.sendStatus(0, status)

    assertThat(said.values.count { it }).isAtLeast(4)
    assertThat(elsewhere).isFalse()
    assertThat(withChecks).isEqualTo(alone)
  }

  /** What the speech engine hears of [audio] (100 ms frames), running [check] every 10 s. */
  private fun recognize(models: SpeechModelStore, audio: FloatArray, check: ((FloatArray) -> Unit)?): String {
    val engine = SherpaRecitationEngine(models.modelFile, models.tokensFile)
    val stream = engine.newStream()
    val out = StringBuilder()
    var frame = 0
    while (frame * FRAME < audio.size) {
      val samples = audio.copyOfRange(frame * FRAME, minOf(audio.size, (frame + 1) * FRAME))
      stream.accept(samples, samples.size, speech = true).forEach { out.append(it.text) }
      frame++
      if (check != null && frame % 100 == 0) check(lastSeconds(audio, frame * 100L))
    }
    stream.finish().forEach { out.append(it.text) }
    stream.close()
    engine.close()
    return out.toString()
  }

  private fun lastSeconds(audio: FloatArray, untilMs: Long): FloatArray {
    val end = minOf(audio.size, (untilMs * SAMPLE_RATE / 1000).toInt())
    return audio.copyOfRange(maxOf(0, end - 8 * SAMPLE_RATE), end)
  }

  private fun wordEnds(): List<Long> {
    val json = instrumentation.context.assets.open("clips/manifest.json").use { String(it.readBytes()) }
    val clip = JSONObject(json).getJSONArray("clips").getJSONObject(0)
    check(clip.getString("file") == "mulk_1_5_husary.wav")
    val ends = clip.getJSONArray("word_ends_ms")
    return (0 until ends.length()).map { ends.getLong(it) }
  }

  /** From the test's assets when the run bundled the model (as in Test Lab), else downloaded. */
  private fun installSpeechModel(models: SpeechModelStore) {
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
    var offset = 12
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

  private companion object {
    const val TAG = "MemorizeDoubleCheck"
    const val SAMPLE_RATE = 16_000
    const val FRAME = SAMPLE_RATE / 10
  }
}
