package com.quran.mobile.feature.memorize.recording

import com.quran.data.model.SuraAyah
import com.quran.mobile.feature.memorize.session.MemorizeState
import com.quran.mobile.feature.memorize.tracker.WordId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * How recordings are saved and exported, e.g. for training speech models.
 *
 * Alignment (one JSON file per recording):
 * ```
 * { "format": "quran-memorize-alignment", "version": 1, "id": "…", "session": "…",
 *   "mode": "memorize" | "salah", "recorded_at_ms": 1761160800000, "sample_rate": 16000,
 *   "start": "67:1", "end": "67:15",
 *   "audio": [ { "file": "…_1.m4a", "duration_ms": 61200 }, … ],
 *   "words": [ { "sura": 67, "ayah": 1, "word": 1, "start_ms": 1240, "end_ms": 1610,
 *                "status": "recited" | "hinted" | "missed", "phonemes": "…" }, … ] }
 * ```
 * The audio files play one after the other (one per stretch of listening); word times count from the
 * start of the first. Times come from the on-device recognizer: about a tenth of a second, good
 * enough to cut segments or to start a forced alignment. Phonemes are in the speech model's phonetic
 * script (quran-transcript); sura:ayah:word joins the words with any Quran text.
 *
 * Manifest ([manifest]): JSON lines as used by NVIDIA NeMo's speech recognition datasets, one per
 * ayah the user recited entirely and within one audio file:
 * `{"audio_filepath": "…", "offset": 1.04, "duration": 4.86, "text": "…", "sura": 67, "ayah": 1}`.
 */
internal object RecordingFormat {
  private const val FORMAT = "quran-memorize-alignment"
  private const val VERSION = 1
  /** Kept around a segment so its first and last sounds aren't cut. */
  private const val PADDING_BEFORE_MS = 200L
  private const val PADDING_AFTER_MS = 300L

  fun write(recording: Recording, audioPath: (Recording.Part) -> String = { it.file.name }): String =
    JSONObject()
      .put("format", FORMAT)
      .put("version", VERSION)
      .put("id", recording.id)
      .put("session", recording.session)
      .put("mode", recording.mode.name.lowercase(Locale.US))
      .put("recorded_at_ms", recording.startedAt)
      .put("sample_rate", 16_000)
      .put("start", text(recording.start))
      .put("end", text(recording.end))
      .put("audio", JSONArray(recording.parts.map { JSONObject().put("file", audioPath(it)).put("duration_ms", it.durationMs) }))
      .put("words", JSONArray(recording.words.map { word ->
        JSONObject()
          .put("sura", word.id.sura)
          .put("ayah", word.id.ayah)
          .put("word", word.id.word)
          .put("start_ms", word.startMs ?: JSONObject.NULL)
          .put("end_ms", word.endMs ?: JSONObject.NULL)
          .put("status", word.status.name.lowercase(Locale.US))
          .put("phonemes", word.phonemes)
      }))
      .toString(1)

  fun read(json: String, directory: File): Recording {
    val root = JSONObject(json)
    require(root.getString("format") == FORMAT)
    val audio = root.getJSONArray("audio")
    val words = root.getJSONArray("words")
    return Recording(
      id = root.getString("id"),
      session = root.getString("session"),
      mode = MemorizeState.Mode.valueOf(root.getString("mode").uppercase(Locale.US)),
      startedAt = root.getLong("recorded_at_ms"),
      start = ayah(root.getString("start")),
      end = ayah(root.getString("end")),
      parts = (0 until audio.length()).map { i ->
        val part = audio.getJSONObject(i)
        Recording.Part(File(directory, part.getString("file")), part.getLong("duration_ms"))
      },
      words = (0 until words.length()).map { i ->
        val word = words.getJSONObject(i)
        Recording.Word(
          id = WordId(word.getInt("sura"), word.getInt("ayah"), word.getInt("word")),
          startMs = if (word.isNull("start_ms")) null else word.getLong("start_ms"),
          endMs = if (word.isNull("end_ms")) null else word.getLong("end_ms"),
          status = Recording.WordStatus.valueOf(word.getString("status").uppercase(Locale.US)),
          phonemes = word.getString("phonemes"),
        )
      },
    )
  }

  /** NeMo manifest lines for the ayat of [recording] recited entirely by the user. */
  fun manifest(recording: Recording, audioPath: (Recording.Part) -> String): List<String> {
    // where each part starts and ends in the recording
    val bounds = recording.parts.runningFold(0L) { start, part -> start + part.durationMs }
    return recording.words.groupBy { it.id.sura to it.id.ayah }.mapNotNull { (key, words) ->
      if (words.any { it.status != Recording.WordStatus.RECITED || it.startMs == null || it.endMs == null }) return@mapNotNull null
      // only a whole ayah: from its first word
      if (words.first().id.word != 1) return@mapNotNull null
      val start = words.minOf { it.startMs!! }
      val end = words.maxOf { it.endMs!! }
      val part = (0 until recording.parts.size).firstOrNull { start >= bounds[it] && end <= bounds[it + 1] }
        ?: return@mapNotNull null
      val from = maxOf(bounds[part], start - PADDING_BEFORE_MS) - bounds[part]
      val to = minOf(bounds[part + 1], end + PADDING_AFTER_MS) - bounds[part]
      JSONObject()
        .put("audio_filepath", audioPath(recording.parts[part]))
        .put("offset", from / 1000.0)
        .put("duration", (to - from) / 1000.0)
        .put("text", words.joinToString(" ") { it.phonemes })
        .put("sura", key.first)
        .put("ayah", key.second)
        .toString()
    }
  }

  private fun text(ayah: SuraAyah) = "${ayah.sura}:${ayah.ayah}"

  private fun ayah(text: String) = text.split(':').let { SuraAyah(it[0].toInt(), it[1].toInt()) }
}
