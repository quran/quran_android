package com.quran.mobile.feature.memorize.recording

import com.quran.mobile.feature.memorize.tracker.WordId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * What the recognizer heard and what the session did during a recording made to contribute ("Record
 * and contribute"), kept next to it as JSON lines (`<id>.heard.jsonl`, `heard.jsonl` in an export):
 * ```
 * {"format": "quran-memorize-heard", "version": 1, "recording": "…", "session": "…", "sample_rate": 16000,
 *  "app_version": "…", "device": "…", "android_sdk": 34, "model": "…", "model_sha256": "…", "settings": {…}}
 * ["t", 1240, "بِ", 0.97]                            a token the recognizer heard: time, text, probability (or null)
 * ["e", 2100, "mistake", "67:1:3", "substitution"]  an event: time, kind, word (or null), detail (or null)
 * ```
 * Times are in ms from the start of the recording's audio, as its words' in the alignment. Tokens
 * are the speech model's own output (its phonetic script), before the app compares them with the
 * Quran; events are what the app decided from them (see [SessionEvent.Kind], written in lower case).
 */
internal object RecordingDetails {
  const val FORMAT = "quran-memorize-heard"
  private const val VERSION = 1
  private const val SAMPLES_PER_MS = 16L

  /** A detail at [sample] of the session's recorded audio. */
  sealed interface Item {
    val sample: Long
  }

  data class Token(override val sample: Long, val text: String, val probability: Float?) : Item

  data class Event(override val sample: Long, val kind: SessionEvent.Kind, val word: WordId?, val detail: String?) : Item

  /** Writes [items] of a recording whose audio starts at [startSample]; replaces the file at once. */
  fun write(file: File, header: JSONObject, items: List<Item>, startSample: Long) {
    val partial = File(file.path + ".part")
    partial.bufferedWriter().use { out ->
      out.write(JSONObject(header.toString()).put("format", FORMAT).put("version", VERSION).put("sample_rate", 16_000).toString())
      out.write("\n")
      for (item in items.sortedBy { it.sample }) {
        val ms = ((item.sample - startSample) / SAMPLES_PER_MS).coerceAtLeast(0)
        val line = when (item) {
          is Token -> JSONArray().put("t").put(ms).put(item.text).put(item.probability?.toDouble() ?: JSONObject.NULL)
          is Event -> JSONArray().put("e").put(ms).put(item.kind.name.lowercase(Locale.US))
            .put(item.word?.let { "${it.sura}:${it.ayah}:${it.word}" } ?: JSONObject.NULL)
            .put(item.detail ?: JSONObject.NULL)
        }
        out.write(line.toString())
        out.write("\n")
      }
    }
    partial.renameTo(file)
  }

  /** The header and the lines of a details file, for tests and tools. */
  fun read(file: File): Pair<JSONObject, List<JSONArray>> {
    val lines = file.readLines().filter { it.isNotBlank() }
    val header = JSONObject(lines.first())
    require(header.getString("format") == FORMAT)
    return header to lines.drop(1).map(::JSONArray)
  }
}
