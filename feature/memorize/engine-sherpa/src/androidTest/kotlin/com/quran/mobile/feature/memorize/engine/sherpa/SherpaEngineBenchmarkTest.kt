package com.quran.mobile.feature.memorize.engine.sherpa

import android.os.Bundle
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.quran.mobile.feature.memorize.tracker.PhonemeNormalizer
import com.quran.mobile.feature.memorize.tracker.RecitationTracker
import com.quran.mobile.feature.memorize.tracker.TimedPhoneme
import com.quran.mobile.feature.memorize.tracker.TrackerEvent
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * On-device benchmark of the speech engine (run on real phones, e.g. Firebase Test Lab):
 * recognition speed (real-time factor) for 1, 2 and 4 threads, native memory, and whether the
 * tracker follows a known recitation. Results are reported as instrumentation status and in logcat
 * (tag MemorizeBenchmark).
 */
@RunWith(AndroidJUnit4::class)
class SherpaEngineBenchmarkTest {

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val testContext = InstrumentationRegistry.getInstrumentation().context

  @Test
  fun benchmark() {
    val (model, tokens) = modelFiles()
    val audio = loadWav("fatiha_husary_30s.wav")
    val results = Bundle()

    for (threads in listOf(1, 2, 4)) {
      val heapBefore = Debug.getNativeHeapAllocatedSize()
      val loadStart = System.nanoTime()
      val engine = SherpaRecitationEngine(model, tokens, threads)
      val loadMs = (System.nanoTime() - loadStart) / 1e6
      val stream = engine.newStream()

      val tracker = RecitationTracker(FATIHA.map { PhonemeNormalizer.normalize(it, false) }, phrases = PHRASES)
      val recited = mutableListOf<Int>()
      val chunk = 1_600 // 100 ms, as the microphone delivers it
      var worstChunkMs = 0.0
      val start = System.nanoTime()
      var offset = 0
      while (offset < audio.size) {
        val count = minOf(chunk, audio.size - offset)
        val t0 = System.nanoTime()
        val tokensOut = stream.accept(audio.copyOfRange(offset, offset + count))
        worstChunkMs = maxOf(worstChunkMs, (System.nanoTime() - t0) / 1e6)
        val phonemes = tokensOut.flatMap { token ->
          PhonemeNormalizer.normalize(token.text, false).map { TimedPhoneme(it, token.timeMs) }
        }
        tracker.feed(phonemes).filterIsInstance<TrackerEvent.WordRecited>().forEach { recited += it.index }
        offset += count
      }
      stream.finish()
      val seconds = (System.nanoTime() - start) / 1e9
      val rtf = seconds / (audio.size / 16_000.0)
      val heapMb = (Debug.getNativeHeapAllocatedSize() - heapBefore) / 1e6
      stream.close()
      engine.close()

      val line = "threads=$threads rtf=%.3f load_ms=%.0f worst_chunk_ms=%.1f native_heap_mb=%.1f words=%d/%d"
        .format(rtf, loadMs, worstChunkMs, heapMb, recited.size, FATIHA.size)
      Log.i(TAG, line)
      results.putString("threads_$threads", line)

      // 30 s of Husary covers the isti'adha and the first ayat of Al-Fatiha
      assertThat(recited.take(8)).containsExactly(0, 1, 2, 3, 4, 5, 6, 7).inOrder()
      assertThat(rtf).isLessThan(1.0)
    }
    InstrumentationRegistry.getInstrumentation().sendStatus(0, results)
  }

  private fun modelFiles(): Pair<File, File> {
    val directory = File(context.cacheDir, "memorize-benchmark").apply { mkdirs() }
    val model = modelFile(directory, "quran-stt-int8.onnx", MODEL_SHA256)
    val tokens = modelFile(directory, "tokens.txt", TOKENS_SHA256)
    return model to tokens
  }

  /** From the test's assets when the run bundled the model (as in Test Lab), else downloaded. */
  private fun modelFile(directory: File, name: String, sha256: String): File {
    val file = File(directory, name)
    if (!file.exists() || sha(file) != sha256) {
      val bundled = testContext.assets.list("").orEmpty().contains(name)
      val input = if (bundled) {
        testContext.assets.open(name)
      } else {
        URL("$MODEL_BASE/$name").openConnection().apply {
          connectTimeout = 30_000
          readTimeout = 60_000
        }.getInputStream()
      }
      input.use { source -> file.outputStream().use { source.copyTo(it) } }
    }
    assertThat(sha(file)).isEqualTo(sha256)
    return file
  }

  private fun sha(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buffer = ByteArray(1 shl 16)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  /** 16 kHz mono 16-bit PCM WAV -> floats in [-1, 1]. */
  private fun loadWav(asset: String): FloatArray {
    val bytes = testContext.assets.open(asset).use { it.readBytes() }
    val buffer = ByteBuffer.wrap(bytes, WAV_HEADER, bytes.size - WAV_HEADER).order(ByteOrder.LITTLE_ENDIAN)
    return FloatArray((bytes.size - WAV_HEADER) / 2) { buffer.short / 32_768f }
  }

  private companion object {
    const val TAG = "MemorizeBenchmark"
    const val WAV_HEADER = 44
    const val MODEL_BASE =
      "https://huggingface.co/Saboorhsn/quran-stt-onnx/resolve/51ec9bcddeaf1c480bbe39064a2a3b88bc2ff224"
    const val MODEL_SHA256 = "31755836528da336a6192121cd7bc82cb41752dddb65566fd000b89c8686da6b"
    const val TOKENS_SHA256 = "252c10687e442aa9291973065fae19fa39bcd681c4f5612ec496a647e20b43a1"

    // Al-Fatiha as pronounced (lexicon_v1, in context)
    val FATIHA = listOf(
      "بِسمِ", "للَااهِ", "ررَحمَاانِ", "ررَحِۦۦۦۦم", "ءَلحَمدُ", "لِللَااهِ", "رَببِ", "لعَاالَمِۦۦۦۦن",
      "ءَررَحمَاانِ", "ررَحِۦۦۦۦم", "مَاالِكِ", "يَومِ", "ددِۦۦۦۦن", "ءِييَااكَ", "نَعبُدُ", "وَءِييَااكَ",
      "نَستَعِۦۦۦۦن", "ءِهدِنَ", "صصِرَااطَ", "لمُستَقِۦۦۦۦم", "صِرَااطَ", "للَذِۦۦنَ", "ءَنعَمتَ", "عَلَيهِم",
      "غَيرِ", "لمَغضُۥۥبِ", "عَلَيهِم", "وَلَ", "ضضَااااااللِۦۦۦۦن",
    )
    val PHRASES = listOf(
      "isti'adha" to PhonemeNormalizer.normalize("ءَعُۥۥذُ بِللَااهِ مِنَ ششَيطَاانِ ررَجِۦۦۦۦم", false)
    )
  }
}
