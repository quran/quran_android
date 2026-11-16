package com.quran.mobile.feature.memorize.recording

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.settings.MemorizePreferences
import com.quran.mobile.feature.memorize.tracker.WordId
import com.quran.mobile.feature.memorize.tracker.engine.RecognizedToken
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
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    // Al-Ikhlas from its start, its end reported a moment after it was said; then the basmala and
    // Al-Falaq
    audio(session, 3_000)
    session.word(WordId(112, 1, 1), 100, 400, Recording.WordStatus.RECITED, "qul", endsSura = false)
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
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    audio(session, 3_000)
    session.word(WordId(112, 1, 1), 100, 400, Recording.WordStatus.RECITED, "qul", endsSura = false)
    session.word(WordId(112, 4, 5), 2_000, 2_400, Recording.WordStatus.RECITED, "ahad", endsSura = true)
    session.word(WordId(113, 1, 1), 2_600, 2_900, Recording.WordStatus.RECITED, "qul", endsSura = false)
    session.finish()

    val recording = store.list().single()
    assertThat(recording.start to recording.end).isEqualTo(SuraAyah(112, 1) to SuraAyah(113, 1))
    assertThat(recording.durationMs).isEqualTo(3_000)
  }

  @Test
  fun `what was recorded before a pause can be played while the session goes on`() = runTest {
    val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.SALAH, contribute = false, info = emptyMap())
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
      val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
      audio(session, 1_000)
      session.word(WordId(67, 1, 1), 100, 500, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
      session.finish()
    }
    val other = store.start(SuraAyah(68, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    audio(other, 1_000)
    other.word(WordId(68, 1, 1), 100, 500, Recording.WordStatus.RECITED, "nun", endsSura = false)
    other.finish()

    assertThat(store.list().map { it.start }).containsExactly(SuraAyah(68, 1), SuraAyah(67, 1))
    assertThat(store.list().first { it.start.sura == 67 }.startedAt).isEqualTo(1_120_000)
  }

  @Test
  fun `recordings are kept until deleted by default`() = runTest {
    repeat(2) {
      val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
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
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
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

  @Test
  fun `a recording starts where the user started, not where the session looked from`() = runTest {
    // the session may start anywhere from a few pages before the one on screen
    val session = store.start(SuraAyah(65, 6), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    audio(session, 2_000)
    session.word(WordId(67, 1, 1), 500, 900, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
    session.word(WordId(67, 1, 2), 950, 1_200, Recording.WordStatus.RECITED, "lladhi", endsSura = false)
    session.finish()

    val recording = store.list().single()
    assertThat(recording.start to recording.end).isEqualTo(SuraAyah(67, 1) to SuraAyah(67, 1))
    assertThat(recording.words).hasSize(2)
  }

  @Test
  fun `a recording keeps how many times a mistake was marked on each word`() = runTest {
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    audio(session, 2_000)
    session.word(WordId(112, 1, 1), 100, 400, Recording.WordStatus.RECITED, "qul", endsSura = false, mistakes = 2)
    session.word(WordId(112, 1, 2), 500, 800, Recording.WordStatus.HINTED, "huwa", endsSura = false)
    session.finish()

    val recording = store.list().single()
    assertThat(recording.words.map { it.mistakes }).containsExactly(2, 0).inOrder()
    assertThat(recording.contribute).isFalse()
    assertThat(recording.details).isNull()
    val sessions = store.sessions()
    assertThat(sessions.single().mistakes).isEqualTo(1)
    assertThat(sessions.single().hinted).isEqualTo(1)
  }

  @Test
  fun `recordings made before mistakes were kept still read`() = runTest {
    val directory = File(context.noBackupFilesDir, "memorize/recordings").apply { mkdirs() }
    File(directory, "old_1.m4a").writeBytes(ByteArray(10))
    File(directory, "old.json").writeText(
      """{"format": "quran-memorize-alignment", "version": 1, "id": "old", "session": "s", "mode": "salah",
        | "recorded_at_ms": 5, "sample_rate": 16000, "start": "67:1", "end": "67:2",
        | "audio": [{"file": "old_1.m4a", "duration_ms": 1000}],
        | "words": [{"sura": 67, "ayah": 1, "word": 1, "start_ms": 10, "end_ms": 20, "status": "recited", "phonemes": "x"}]}
      """.trimMargin()
    )

    val recording = store.list().single()
    assertThat(recording.mode).isEqualTo(MemorizeState.Mode.SALAH)
    assertThat(recording.words.single().mistakes).isNull()
    assertThat(recording.contribute).isFalse()
  }

  @Test
  fun `a contributed recording keeps what was heard, in the sura's recording it is about`() = runTest {
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE, contribute = true, info = mapOf("model" to "m"))
    assertThat(session.keepsDetails).isTrue()
    audio(session, 3_000)
    session.heard(listOf(RecognizedToken("qu", 150, 0.9f), RecognizedToken("l", 350, null)))
    session.word(WordId(112, 1, 1), 100, 400, Recording.WordStatus.RECITED, "qul", endsSura = false)
    session.event(SessionEvent(2_100, SessionEvent.Kind.MISTAKE, WordId(112, 4, 5), "substitution"))
    session.word(WordId(112, 4, 5), 2_000, 2_400, Recording.WordStatus.RECITED, "ahad", endsSura = true)
    // reported a moment later: the next sura's basmala, said after Al-Ikhlas's recording ended
    audio(session, 1_000)
    session.heard(listOf(RecognizedToken("bis", 3_400, 0.8f)))
    audio(session, 3_000)
    session.word(WordId(113, 1, 1), 6_200, 6_500, Recording.WordStatus.RECITED, "qul", endsSura = false)
    audio(session, 1_000)
    session.finish()

    val recordings = store.list().sortedBy { it.start.sura }
    assertThat(recordings.map { it.contribute }).containsExactly(true, true)
    val (header, first) = RecordingDetails.read(recordings[0].details!!)
    assertThat(header.getString("model")).isEqualTo("m")
    assertThat(header.getString("recording")).isEqualTo(recordings[0].id)
    assertThat(header.getString("session")).isEqualTo(session.session)
    assertThat(header.has("app_version")).isTrue()
    assertThat(first.map { it.toString() }).containsExactly(
      """["t",150,"qu",0.8999999761581421]""",
      """["t",350,"l",null]""",
      """["e",2100,"mistake","112:4:5","substitution"]""",
    ).inOrder()
    // Al-Falaq's recording starts at 2.9 s
    val (_, second) = RecordingDetails.read(recordings[1].details!!)
    assertThat(second.map { it.toString() }).containsExactly("""["t",500,"bis",0.800000011920929]""")
  }

  @Test
  fun `details after a pause stay with the recording, at its own times`() = runTest {
    val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE, contribute = true, info = emptyMap())
    audio(session, 1_000)
    session.word(WordId(67, 1, 1), 100, 500, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
    session.pause()
    audio(session, 1_000)
    session.heard(listOf(RecognizedToken("x", 1_500, null)))
    session.finish()

    val recording = store.list().single()
    assertThat(recording.parts).hasSize(2)
    val (_, lines) = RecordingDetails.read(recording.details!!)
    assertThat(lines.map { it.toString() }).containsExactly("""["e",1000,"paused",null,null]""", """["t",1500,"x",null]""").inOrder()
  }

  @Test
  fun `word times stay right when a busy phone dropped some audio`() = runTest {
    val executor = QueueExecutor()
    store = RecordingStore.forTest(context, preferences, ::RawWriter, { executor }, { now })
    val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    // 31 s of audio while the recorder is stuck: the last second of it is dropped
    audio(session, 31_000)
    // said in the second that was dropped, and after it
    session.word(WordId(67, 1, 1), 30_500, 30_800, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
    // the recorder catches up
    executor.runAll()
    audio(session, 1_000)
    session.word(WordId(67, 1, 2), 31_200, 31_600, Recording.WordStatus.RECITED, "lladhi", endsSura = false)
    session.finish()
    executor.runAll()

    val recording = store.list().single()
    assertThat(recording.durationMs).isEqualTo(31_000)
    assertThat(recording.words.map { it.startMs to it.endMs }).containsExactly(30_000L to 30_000L, 30_200L to 30_600L).inOrder()
  }

  @Test
  fun `nothing is recorded after the session is over`() = runTest {
    val executor = QueueExecutor()
    store = RecordingStore.forTest(context, preferences, ::RawWriter, { executor }, { now })
    val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    audio(session, 1_000)
    session.word(WordId(67, 1, 1), 100, 500, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
    session.finish()
    // a frame that was on its way when the session ended
    audio(session, 1_000)
    executor.runAll()

    assertThat(store.list().single().durationMs).isEqualTo(1_000)
    val files = File(context.noBackupFilesDir, "memorize/recordings").list().orEmpty()
    assertThat(files.count { it.endsWith(".m4a") }).isEqualTo(1)
  }

  @Test
  fun `sessions group their recordings, and go together`() = runTest {
    val first = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE, contribute = false, info = emptyMap())
    audio(first, 3_000)
    first.word(WordId(112, 4, 5), 2_000, 2_400, Recording.WordStatus.RECITED, "ahad", endsSura = true)
    audio(first, 1_000)
    now += 1_000
    first.word(WordId(113, 1, 1), 3_200, 3_500, Recording.WordStatus.MISSED, "qul", endsSura = false)
    first.finish()
    now += 60_000
    val second = store.start(SuraAyah(67, 1), MemorizeState.Mode.SALAH, contribute = true, info = emptyMap())
    audio(second, 1_000)
    second.word(WordId(67, 1, 1), 100, 500, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
    second.finish()

    val sessions = store.sessions()
    assertThat(sessions.map { it.id }).containsExactly(second.session, first.session).inOrder()
    assertThat(sessions[1].recordings).hasSize(2)
    assertThat(sessions[1].start to sessions[1].end).isEqualTo(SuraAyah(112, 4) to SuraAyah(113, 1))
    assertThat(sessions[1].missed).isEqualTo(1)
    assertThat(sessions.map { it.contribute }).containsExactly(true, false).inOrder()

    store.deleteSession(first.session)
    assertThat(store.sessions().map { it.id }).containsExactly(second.session)
    val files = File(context.noBackupFilesDir, "memorize/recordings").list().orEmpty()
    assertThat(files.filter { !it.startsWith(store.list().single().id) }).isEmpty()
  }

  @Test
  fun `a contributed recording is never replaced`() = runTest {
    preferences.keepRecordings = MemorizePreferences.KeepRecordings.REPLACE_SAME_PLACE
    listOf(true, false).forEach { contribute ->
      now += 60_000
      val session = store.start(SuraAyah(67, 1), MemorizeState.Mode.MEMORIZE, contribute = contribute, info = emptyMap())
      audio(session, 1_000)
      session.word(WordId(67, 1, 1), 100, 500, Recording.WordStatus.RECITED, "tabaraka", endsSura = false)
      session.finish()
    }
    assertThat(store.list().map { it.contribute }).containsExactly(false, true).inOrder()
  }

  @Test
  fun `the export has a contributed recording's details, and training segments end before a hint`() = runTest {
    val session = store.start(SuraAyah(112, 1), MemorizeState.Mode.MEMORIZE, contribute = true, info = emptyMap())
    audio(session, 4_000)
    session.heard(listOf(RecognizedToken("qu", 350, 0.9f)))
    session.word(WordId(112, 1, 1), 300, 600, Recording.WordStatus.RECITED, "qul", endsSura = false)
    session.word(WordId(112, 1, 2), 650, 900, Recording.WordStatus.RECITED, "huwa", endsSura = false)
    // the hint voice starts just after the ayah
    session.word(WordId(112, 2, 1), 1_000, null, Recording.WordStatus.HINTED, "allahu", endsSura = false)
    session.finish()

    val entries = unzip(store.list().single())
    val folder = entries.keys.single { it.endsWith("alignment.json") }.substringBefore('/')
    assertThat(entries.keys).contains("$folder/heard.jsonl")
    assertThat(JSONObject(String(entries.getValue("$folder/alignment.json"))).getString("heard")).isEqualTo("heard.jsonl")
    val manifest = String(entries.getValue("manifest.jsonl")).lines().filter { it.isNotBlank() }.map(::JSONObject)
    assertThat(manifest.single().getDouble("duration")).isWithin(0.001).of(0.9)
  }

  private fun audio(session: SessionRecorder, ms: Int) = repeat(ms / 100) { session.write(FloatArray(1_600) { 0.1f }) }

  /** Runs tasks only when asked to, as a phone too busy to keep up. */
  private class QueueExecutor : java.util.concurrent.Executor {
    private val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
      tasks.addLast(command)
    }

    fun runAll() {
      while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }
  }

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
