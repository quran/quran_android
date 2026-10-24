package com.quran.mobile.feature.memorize.recording

import android.content.Context
import com.quran.mobile.common.ui.core.R as CoreR
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Names recordings by where they start and end, and exports them for listening or training. */
object RecordingExport {

  /** E.g. "Al-Mulk 1–15", or "Al-Mulk 1 – Al-Qalam 20" for a session that went on to the next sura. */
  fun label(context: Context, recording: Recording): String {
    val names = context.resources.getStringArray(CoreR.array.sura_names)
    val start = recording.start
    val end = recording.end
    val first = names.getOrNull(start.sura - 1) ?: start.sura.toString()
    return if (start.sura == end.sura) {
      "$first ${start.ayah}–${end.ayah}"
    } else {
      "$first ${start.ayah} – ${names.getOrNull(end.sura - 1) ?: end.sura} ${end.ayah}"
    }
  }

  /**
   * Writes [recordings] as a zip: a folder per recording with its audio and alignment (see
   * [RecordingFormat]), and a NeMo manifest of all their whole ayat at the top. Reads files.
   */
  fun zip(recordings: List<Recording>, output: OutputStream) {
    ZipOutputStream(output).use { zip ->
      val manifest = StringBuilder()
      for (recording in recordings) {
        val folder = folder(recording)
        val path = { part: Recording.Part -> "$folder/${part.file.name.substringAfter('_')}" }
        for (part in recording.parts) {
          zip.putNextEntry(ZipEntry(path(part)))
          part.file.inputStream().use { it.copyTo(zip) }
          zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry("$folder/alignment.json"))
        zip.write(RecordingFormat.write(recording) { part -> part.file.name.substringAfter('_') }.toByteArray())
        zip.closeEntry()
        RecordingFormat.manifest(recording, path).forEach { manifest.append(it).append('\n') }
      }
      zip.putNextEntry(ZipEntry("manifest.jsonl"))
      zip.write(manifest.toString().toByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("README.txt"))
      zip.write(README.toByteArray())
      zip.closeEntry()
    }
  }

  /** A file name for exporting [recordings]. */
  fun fileName(recordings: List<Recording>): String =
    if (recordings.size == 1) "quran-recitation_${folder(recordings[0])}.zip" else "quran-recitations_${stamp(System.currentTimeMillis())}.zip"

  // e.g. 067-001_067-015_20261022-214000
  private fun folder(recording: Recording): String = "%03d-%03d_%03d-%03d_%s".format(
    Locale.US, recording.start.sura, recording.start.ayah, recording.end.sura, recording.end.ayah, stamp(recording.startedAt),
  )

  private fun stamp(time: Long) = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(time))

  private val README = """
    |Recitation recorded with Quran for Android (Memorize).
    |
    |Each folder is one recording: its audio (16 kHz mono AAC, .m4a; the files play one after the
    |other, one per stretch of listening) and alignment.json, which lists every word the session went
    |through with its time in ms from the start of the first file:
    |  sura, ayah, word     where it is in the Quran (join with any Quran text)
    |  start_ms, end_ms     when it was heard (from the on-device recognizer, about 0.1 s)
    |  status               recited (by the reciter), hinted (given by the app) or missed
    |  phonemes             how it is pronounced, in the quran-transcript phonetic script
    |
    |manifest.jsonl is a NVIDIA NeMo speech recognition manifest: one line per ayah recited entirely,
    |with audio_filepath, offset and duration in seconds, and its phonemes as text.
    |""".trimMargin()
}
