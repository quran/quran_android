package com.quran.mobile.feature.memorize.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.quran.data.di.AppScope
import com.quran.data.model.SuraAyah
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorize.tracker.WordId
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The phoneme lexicon (tools/memorize/build_lexicon.py): how every word of the Quran is
 * pronounced, in the phonetic script the speech model emits. Bundled with the app.
 *
 * Reading it throws if the bundled database can't be copied out or opened (e.g. no storage left).
 */
@SingleIn(AppScope::class)
class LexiconRepository private constructor(
  private val context: Context,
  private val io: CoroutineDispatcher,
) {
  @Inject constructor(@ApplicationContext context: Context) : this(context, Dispatchers.IO)
  private val mutex = Mutex()
  private var database: SQLiteDatabase? = null

  /**
   * The words from [start] to the end of its sura, followed by whole suras until there are at least
   * [minWords] and [throughSura] is in (or the Quran ends): the session loads more as the user goes
   * on (see [nextSuras]).
   */
  suspend fun passage(start: SuraAyah, minWords: Int = 0, throughSura: Int = start.sura): Passage = withContext(io) {
    val words = suraWords(start.sura, fromAyah = start.ayah)
    var sura = start.sura
    while ((words.size < minWords || sura < throughSura) && sura < LAST_SURA) {
      sura++
      words += suraWords(sura, fromAyah = 1)
    }
    Passage(words)
  }

  /** Whole suras after [afterSura] with at least [minWords] words between them (fewer at the end). */
  suspend fun nextSuras(afterSura: Int, minWords: Int): Passage = withContext(io) {
    val words = ArrayList<PassageWord>()
    var sura = afterSura
    while (sura < LAST_SURA && (words.isEmpty() || words.size < minWords)) {
      sura++
      words += suraWords(sura, fromAyah = 1)
    }
    Passage(words)
  }

  private suspend fun suraWords(sura: Int, fromAyah: Int): ArrayList<PassageWord> {
    val words = ArrayList<PassageWord>()
    database().rawQuery(
      "SELECT ayah, word, wasl, iso FROM words WHERE sura = ? AND ayah >= ? ORDER BY ayah, word",
      arrayOf(sura.toString(), fromAyah.toString())
    ).use { cursor ->
      while (cursor.moveToNext()) {
        words += PassageWord(
          id = WordId(sura, cursor.getInt(0), cursor.getInt(1)),
          wasl = cursor.getString(2),
          isolated = cursor.getString(3),
        )
      }
    }
    return words
  }

  /** Phrases said around a recitation that aren't part of it, e.g. "isti'adha" or "takbir". */
  suspend fun phrases(kinds: Set<String>): List<Pair<String, String>> = withContext(io) {
    val result = ArrayList<Pair<String, String>>()
    database().rawQuery("SELECT kind, phonemes FROM phrases", null).use { cursor ->
      while (cursor.moveToNext()) {
        val kind = cursor.getString(0)
        if (kind in kinds) result += kind to cursor.getString(1)
      }
    }
    result
  }

  private suspend fun database(): SQLiteDatabase = mutex.withLock {
    database ?: open().also { database = it }
  }

  private fun open(): SQLiteDatabase {
    val file = BundledAssets.extract(context, "memorize/$DATABASE")
    return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
  }

  companion object {
    /** A repository reading on the test's dispatcher. */
    internal fun forTest(context: Context, io: CoroutineDispatcher) = LexiconRepository(context, io)

    private const val DATABASE = "lexicon_v1.db"
    const val LAST_SURA = 114
  }
}
