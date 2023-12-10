package com.quran.labs.androidquran.data

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.database.DatabaseHandler.Companion.getDatabaseHandler
import com.quran.labs.androidquran.database.DatabaseUtils.closeCursor
import com.quran.labs.androidquran.database.TranslationsDBAdapter
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.mobile.translation.model.LocalTranslation
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

class QuranDataProvider : ContentProvider() {
  private var didInject = false

  @Inject lateinit var quranDisplayData: QuranDisplayData
  @Inject lateinit var translationsDBAdapter: TranslationsDBAdapter
  @Inject lateinit var quranFileUtils: QuranFileUtils
  @Inject lateinit var quranInfo: QuranInfo

  override fun onCreate(): Boolean {
    return true
  }

  override fun query(
    uri: Uri, projection: Array<String>?, selection: String?,
    selectionArgs: Array<String>?, sortOrder: String?
  ): Cursor? {
    val context = context
    if (!didInject) {
      val appContext = context?.applicationContext
      didInject = if (appContext is QuranApplication) {
        appContext.applicationComponent.inject(this)
        true
      } else {
        Timber.e("unable to inject QuranDataProvider")
        return null
      }
    }
    Timber.d("uri: %s", uri.toString())
    return when (uriMatcher.match(uri)) {
      SEARCH_SUGGEST -> {
        requireNotNull(selectionArgs) { "selectionArgs must be provided for the Uri: $uri" }
        getSuggestions(selectionArgs[0])
      }

      SEARCH_VERSES -> {
        requireNotNull(selectionArgs) { "selectionArgs must be provided for the Uri: $uri" }
        search(selectionArgs[0])
      }

      else -> {
        throw IllegalArgumentException("Unknown Uri: $uri")
      }
    }
  }

  private fun availableTranslations(): List<LocalTranslation> {
    return runBlocking { translationsDBAdapter.getTranslations().first() }
  }

  private fun getSuggestions(query: String): Cursor? {
    if (query.length < 3) {
      return null
    }
    val queryIsArabic = QuranUtils.doesStringContainArabic(query)
    val haveArabic = queryIsArabic &&
        quranFileUtils.hasTranslation(context!!, QURAN_ARABIC_DATABASE)
    val translations = availableTranslations()
    if (translations.isEmpty() && queryIsArabic && !haveArabic) {
      return null
    }
    val total = translations.size
    val start = if (haveArabic) -1 else 0
    val cols = arrayOf(
      BaseColumns._ID,
      SearchManager.SUGGEST_COLUMN_TEXT_1,
      SearchManager.SUGGEST_COLUMN_TEXT_2,
      SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID
    )
    val mc = MatrixCursor(cols)
    val context = context
    var gotResults = false
    var likelyHaveMoreResults = false
    for (i in start until total) {
      if (gotResults) {
        continue
      }
      var database: String
      if (i < 0) {
        database = QURAN_ARABIC_DATABASE
        if (!quranFileUtils.hasArabicSearchDatabase()) {
          continue
        }
      } else {
        val (_, filename, _, _, _, _, languageCode) = translations[i]
        // skip non-arabic databases if the query is in arabic
        if (queryIsArabic || "ar" == languageCode) {
          // skip arabic databases even when the query is in arabic since
          // searching Arabic tafaseer causes a lot of noise on the search
          // results and is confusing.
          continue
        }
        database = filename
      }
      var suggestions: Cursor? = null
      try {
        suggestions = search(query, database, false)
        if (context != null && suggestions != null && suggestions.moveToFirst()) {
          if (suggestions.count > 5) {
            likelyHaveMoreResults = true
          }
          var results = 0
          do {
            if (results == 5) {
              break
            }
            val sura = suggestions.getInt(1)
            val ayah = suggestions.getInt(2)
            val page = quranInfo.getPageFromSuraAyah(sura, ayah)
            val text = suggestions.getString(3)
            val foundText = context.getString(
              R.string.found_in_sura,
              quranDisplayData.getSuraName(context, sura, false),
              ayah,
              page
            )
            gotResults = true
            val row = mc.newRow()
            val id = suggestions.getInt(0)
            row.add(id)
            row.add(text)
            row.add(foundText)
            row.add(id)
            results++
          } while (suggestions.moveToNext())
        }
      } finally {
        closeCursor(suggestions)
      }
    }
    if (context != null && (queryIsArabic || likelyHaveMoreResults)) {
      mc.addRow(
        arrayOf<Any>(
          -1, context.getString(R.string.search_full_results),
          context.getString(R.string.search_entire_mushaf), -1
        )
      )
    }
    return mc
  }

  private fun search(
    query: String,
    translations: List<LocalTranslation> = availableTranslations()
  ): Cursor? {
    Timber.d("query: %s", query)
    val context = context
    val queryIsArabic = QuranUtils.doesStringContainArabic(query)
    val haveArabic = queryIsArabic &&
        quranFileUtils.hasTranslation(context!!, QURAN_ARABIC_DATABASE)
    if (translations.isEmpty() && queryIsArabic && !haveArabic) {
      return null
    }
    val start = if (haveArabic) -1 else 0
    val total = translations.size
    for (i in start until total) {
      val databaseName: String = if (i < 0) {
        QURAN_ARABIC_DATABASE
      } else {
        val (_, filename, _, _, _, _, languageCode) = translations[i]
        // skip non-arabic databases if the query is in arabic
        if (queryIsArabic || "ar" == languageCode) {
          // skip arabic databases always since it's confusing to people for now.
          // in the future, can think of better ways to enable tafseer search.
          continue
        }
        filename
      }
      val cursor = search(query, databaseName, true)
      if (cursor != null && cursor.count > 0) {
        return cursor
      }
    }
    return null
  }

  private fun search(query: String, databaseName: String, wantSnippets: Boolean): Cursor? {
    val handler = getDatabaseHandler(context!!, databaseName, quranFileUtils)
    return handler.search(query, wantSnippets, QURAN_ARABIC_DATABASE == databaseName)
  }

  override fun getType(uri: Uri): String? {
    return when (uriMatcher.match(uri)) {
      SEARCH_VERSES -> {
        VERSES_MIME_TYPE
      }

      SEARCH_SUGGEST -> {
        SearchManager.SUGGEST_MIME_TYPE
      }

      else -> {
        throw IllegalArgumentException("Unknown URL $uri")
      }
    }
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    throw UnsupportedOperationException()
  }

  override fun update(
    uri: Uri, values: ContentValues?, selection: String?,
    selectionArgs: Array<String>?
  ): Int {
    throw UnsupportedOperationException()
  }

  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
    throw UnsupportedOperationException()
  }

  companion object {
    private const val AUTHORITY = BuildConfig.APPLICATION_ID + ".data.QuranDataProvider"

    @JvmField val SEARCH_URI: Uri = Uri.parse("content://$AUTHORITY/quran/search")
    const val VERSES_MIME_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.quran.labs.androidquran"
    const val QURAN_ARABIC_DATABASE = QuranFileConstants.ARABIC_DATABASE

    // UriMatcher stuff
    private const val SEARCH_VERSES = 0
    private const val SEARCH_SUGGEST = 1
    private val uriMatcher = buildUriMatcher()

    private fun buildUriMatcher(): UriMatcher {
      val matcher = UriMatcher(UriMatcher.NO_MATCH)
      matcher.addURI(AUTHORITY, "quran/search", SEARCH_VERSES)
      matcher.addURI(AUTHORITY, "quran/search/*", SEARCH_VERSES)
      matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST)
      matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST)
      return matcher
    }
  }
}
