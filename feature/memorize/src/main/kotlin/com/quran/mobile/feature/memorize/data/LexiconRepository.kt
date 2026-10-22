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

  /** The words from [start] to the end of its sura. */
  suspend fun passage(start: SuraAyah): Passage = withContext(io) {
    val db = database()
    val words = ArrayList<PassageWord>()
    db.rawQuery(
      "SELECT ayah, word, wasl, iso FROM words WHERE sura = ? AND ayah >= ? ORDER BY ayah, word",
      arrayOf(start.sura.toString(), start.ayah.toString())
    ).use { cursor ->
      while (cursor.moveToNext()) {
        words += PassageWord(
          id = WordId(start.sura, cursor.getInt(0), cursor.getInt(1)),
          wasl = cursor.getString(2),
          isolated = cursor.getString(3),
        )
      }
    }
    Passage(words)
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
  }
}
