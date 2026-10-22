package com.quran.mobile.feature.memorize.recording

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.WordId
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Recordings with a fake encoder (raw samples to a file) and an executor that runs right away, so
 * every step is done when the call returns.
 */
@Config(sdk = [33])
@RunWith(RobolectricTestRunner::class)
class RecordingStoreTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var preferences: MemorizePreferences
  private lateinit var store: RecordingStore
  private var now = 1_000_000L

  @Before
  fun setUp() {
    preferences = MemorizePreferences(context)
    store = RecordingStore.forTest(context, preferences, ::RawWriter, { Executor { it.run() } }, { now })
    store.deleteAll()
  }

  @Test
  fun `a session over two suras gives a recording per sura`() = runTest {
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE)
    // the end of Al-Ikhlas, reported a moment after it was said; then the basmala and Al-Falaq
    audio(session, 3_000)
    session.word(WordId(112, 4, 5), 2_000, 2_400, Recording.WordStatus.RECITED, "ahad", endsSura = true)
    audio(session, 4_000)
    session.word(WordId(113, 1, 1), 6_200, 6_500, Recording.WordStatus.RECITED, "qul", endsSura = false)
    audio(session, 1_000)
    // what follows the last sura heard (e.g. a takbir) isn't a recording of its own
    session.word(WordId(113, 5, 5), 7_200, 7_500, Recording.WordStatus.RECITED, "hasad", endsSura = true)
    audio(session, 1_000)
    session.finish()

    val recordings = store.list().sortedBy { it.start.sura }
    assertThat(recordings.map { it.start to it.end })
      .containsExactly(SuraAyah(112, 1) to SuraAyah(112, 4), SuraAyah(113, 1) to SuraAyah(113, 5)).inOrder()
    // Al-Ikhlas ends half a second after its last word; Al-Falaq's word times count from there
    assertThat(recordings[0].durationMs).isEqualTo(2_900)
    assertThat(recordings[1].words.first().startMs).isEqualTo(3_300)
    assertThat(recordings[1].durationMs).isEqualTo(5_100)
    assertThat(recordings.map { it.session }.toSet()).containsExactly(session.session)
  }

  @Test
  fun `a session can be one recording`() = runTest {
    preferences.recordingSplit = RecordingSplit.BY_SESSION
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE)
    audio(session, 3_000)
    session.word(WordId(112, 4, 5), 2_000, 2_400, Recording.WordStatus.RECITED, "ahad", endsSura = true)
    session.word(WordId(113, 1, 1), 2_600, 2_900, Recording.WordStatus.RECITED, "qul", endsSura = false)
    session.finish()

    val recording = store.list().single()
    assertThat(recording.start to recording.end).isEqualTo(SuraAyah(112, 1) to SuraAyah(113, 1))
    assertThat(recording.durationMs).isEqualTo(3_000)
  }

  @Test
  fun `what was recorded before a pause can be played while the session goes on`() = runTest {
    val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.SALAH)
    audio(session, 2_000)
    session.word(WordId(67, 1, 1), 500, 900, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
    session.pause()

    val played = store.session(session.session)!!
    assertThat(played.durationMs).isEqualTo(2_000)
    assertThat(played.ayahAt(1_000)).isEqualTo(SuraAyah(67, 1))

    audio(session, 1_000)
    session.pause()
    assertThat(store.session(session.session)!!.parts).hasSize(2)
  }

  @Test
  fun `a new recording can replace the older one of the same place`() = runTest {
    preferences.keepRecordings = MemorizePreferences.KeepRecordings.REPLACE_SAME_PLACE
    repeat(2) {
      now += 60_000
      val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE)
      audio(session, 1_000)
      session.word(WordId(67, 1, 1), 100, 500, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
      session.finish()
    }
    val other = store.start(SuraAyah(68, 1), MemorizeState.Mode.MEMORIZE)
    audio(other, 1_000)
    other.word(WordId(68, 1, 1), 100, 500, Recording.WordStatus.RECITED, "nun", endsSura = false)
    other.finish()

    assertThat(store.list().map { it.start }).containsExactly(SuraAyah(68, 1), SuraAyah(67, 1))
    assertThat(store.list().first { it.start.sura == 67 }.startedAt).isEqualTo(1_120_000)
  }

  @Test
  fun `recordings are kept until deleted by default`() = runTest {
    repeat(2) {
      val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE)
      audio(session, 1_000)
      session.word(WordId(67, 1, 1), 100, 500, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
      session.finish()
    }
    assertThat(store.list()).hasSize(2)

    store.delete(store.list().first().id)
    assertThat(store.list()).hasSize(1)
  }

  @Test
  fun `the export has the audio, its alignment and a training manifest of whole ayat`() = runTest {
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE)
    audio(session, 4_000)
    // ayah 1 recited entirely; ayah 2 with a hinted word
    session.word(WordId(112, 1, 1), 300, 600, Recording.WordStatus.RECITED, "qul", endsSura = false)
    session.word(WordId(112, 1, 2), 650, 900, Recording.WordStatus.RECITED, "huwa", endsSura = false)
    session.word(WordId(112, 2, 1), 1_500, null, Recording.WordStatus.HINTED, "allahu", endsSura = false)
    session.finish()
    val recording = store.list().single()

    val entries = unzip(recording)

    val alignment = JSONObject(String(entries.getValue(entries.keys.single { it.endsWith("alignment.json") })))
    assertThat(alignment.getString("format")).isEqualTo("quran-memorize-alignment")
    assertThat(alignment.getJSONArray("words").length()).isEqualTo(3)
    assertThat(alignment.getJSONArray("words").getJSONObject(2).getString("status")).isEqualTo("hinted")
    val manifest = String(entries.getValue("manifest.jsonl")).lines().filter { it.isNotBlank() }.map(::JSONObject)
    assertThat(manifest).hasSize(1)
    assertThat(manifest[0].getString("text")).isEqualTo("qul huwa")
    assertThat(manifest[0].getDouble("offset")).isWithin(0.001).of(0.1)
    assertThat(manifest[0].getDouble("duration")).isWithin(0.001).of(1.1)
    assertThat(entries.keys).contains(manifest[0].getString("audio_filepath"))
    assertThat(entries.keys).contains("README.txt")
  }

  private fun audio(session: SessionRecorder, ms: Int) = repeat(ms / 100) { session.write(FloatArray(1_600) { 0.1f }) }

  private fun unzip(recording: Recording): Map<String, ByteArray> {
    val bytes = ByteArrayOutputStream().also { RecordingExport.zip(listOf(recording), it) }.toByteArray()
    val entries = HashMap<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: break
        entries[entry.name] = zip.readBytes()
      }
    }
    return entries
  }

  /** Writes the samples as they are. */
  private class RawWriter(file: File) : AudioFileWriter {
    private val out = file.outputStream()
    private var wrote = false

    override fun write(pcm: ShortArray, count: Int) {
      for (i in 0 until count) out.write(pcm[i].toInt() and 0xff)
      wrote = wrote || count > 0
    }

    override fun close(): Boolean {
      out.close()
      return wrote
    }
  }

  private fun interface Executor : java.util.concurrent.Executor
}
