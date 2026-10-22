package com.quran.mobile.feature.memorize.data

import android.database.sqlite.SQLiteDatabase
import com.quran.data.core.QuranFileManager
import com.quran.data.di.AppScope
import com.quran.mobile.feature.memorize.glyph.GlyphTypes
import com.quran.mobile.feature.memorize.glyph.HafsGlyphTypeResolver
import com.quran.page.common.data.coordinates.GlyphSize
import com.quran.page.common.data.coordinates.GlyphTypeResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Where each word is on the pages; replaced by a fake in tests. */
fun interface LineSource {
  /**
   * The line (`page * 100 + line`) of every word of the ayat of [suras], keyed by
   * [PageLines.key] (index: word number - 1), as far as it is known.
   */
  suspend fun forSuras(suras: IntRange): Map<Int, IntArray>
}

/**
 * Where each word is on the pages of the page type in use: its line, as `page * 100 + line`. Used
 * to give "the rest of the line" as a hint. Every page type breaks its lines differently, so this
 * reads the page type's own ayahinfo database (any width: they share their lines).
 */
@SingleIn(AppScope::class)
class PageLines private constructor(
  private val fileManager: QuranFileManager,
  private val glyphTypes: HafsGlyphTypeResolver,
  private val io: CoroutineDispatcher,
) : LineSource {
  @Inject constructor(fileManager: QuranFileManager, glyphTypes: HafsGlyphTypeResolver) :
    this(fileManager, glyphTypes, Dispatchers.IO)

  /**
   * The line of every word of the ayat of [suras], keyed by [key] (index: word number - 1); empty
   * when the page type's database isn't there or can't be read.
   */
  override suspend fun forSuras(suras: IntRange): Map<Int, IntArray> = withContext(io) {
    val file = database() ?: return@withContext emptyMap()
    runCatching {
      SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db -> read(db, suras) }
    }.getOrDefault(emptyMap())
  }

  private fun read(db: SQLiteDatabase, suras: IntRange): Map<Int, IntArray> {
    val nativeTypes = runCatching {
      db.rawQuery("SELECT glyph_type FROM glyphs LIMIT 1", null).use { it.moveToFirst() && !it.isNull(0) }
    }.getOrDefault(false)
    val result = HashMap<Int, IntArray>()
    db.rawQuery(
      "SELECT sura_number, ayah_number, page_number, line_number, max_x - min_x, max_y - min_y" +
        (if (nativeTypes) ", glyph_type" else "") +
        " FROM glyphs WHERE sura_number BETWEEN ? AND ? ORDER BY sura_number, ayah_number, position",
      arrayOf(suras.first.toString(), suras.last.toString())
    ).use { cursor ->
      val ayah = ArrayList<Glyph>()
      fun flush() {
        val first = ayah.firstOrNull() ?: return
        wordLines(first.sura, first.ayah, ayah)?.let { result[key(first.sura, first.ayah)] = it }
        ayah.clear()
      }
      while (cursor.moveToNext()) {
        val glyph = Glyph(
          sura = cursor.getInt(0),
          ayah = cursor.getInt(1),
          line = cursor.getInt(2) * 100 + cursor.getInt(3),
          size = GlyphSize(cursor.getInt(4), cursor.getInt(5)),
          isWord = if (nativeTypes) cursor.getString(6) == NATIVE_WORD else null,
        )
        if (ayah.isNotEmpty() && (ayah[0].sura != glyph.sura || ayah[0].ayah != glyph.ayah)) flush()
        ayah += glyph
      }
      flush()
    }
    return result
  }

  private fun wordLines(sura: Int, ayah: Int, glyphs: List<Glyph>): IntArray? {
    if (glyphs.all { it.isWord != null }) {
      return glyphs.filter { it.isWord == true }.map { it.line }.toIntArray()
    }
    val sequence = glyphTypes.sequence(sura, ayah) ?: return null
    val types = GlyphTypes.derive(sequence, glyphs.map { it.size }) ?: return null
    return glyphs.filterIndexed { i, _ -> types[i] == GlyphTypeResolver.WORD }.map { it.line }.toIntArray()
  }

  // the database for this screen's width, or another width's (they share their lines)
  private fun database(): File? {
    val current = fileManager.ayahInfoFileDirectory()
    if (current.isFile) return current
    return current.parentFile?.listFiles { file -> file.name.matches(AYAHINFO) }?.minByOrNull { it.name }
  }

  private class Glyph(val sura: Int, val ayah: Int, val line: Int, val size: GlyphSize, val isWord: Boolean?)

  companion object {
    fun key(sura: Int, ayah: Int) = sura * 1000 + ayah

    private val AYAHINFO = Regex("ayahinfo_\\d+\\.db")
    // the glyph_type of words in databases that have glyph types
    private const val NATIVE_WORD = "word"
  }
}
