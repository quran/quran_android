package com.quran.labs.androidquran.data

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.BaseColumns
import androidx.annotation.VisibleForTesting
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
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.Locale

@HasMemberInjections
open class QuranDataProvider : ContentProvider() {
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
    val uriMatch = uriMatcher.match(uri)
    Timber.d("querying uri match: %d", uriMatch)
    return when (uriMatch) {
      SEARCH_SUGGEST -> {
        requireNotNull(selectionArgs) { "selectionArgs must be provided for the Uri: $uri" }
        getSuggestions(selectionArgs[0])
      }

      SEARCH_VERSES -> {
        requireNotNull(selectionArgs) { "selectionArgs must be provided for the Uri: $uri" }
        val rawArabic = uri.getBooleanQueryParameter(QUERY_PARAM_RAW_ARABIC, false)
        search(selectionArgs[0], rawArabic = rawArabic)
      }

      SEARCH_TAFSIR -> {
        requireNotNull(selectionArgs) { "selectionArgs must be provided for the Uri: $uri" }
        searchTafsir(selectionArgs[0])
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
    val haveArabic = queryIsArabic && quranFileUtils.hasArabicSearchDatabase()
    val translations = if (queryIsArabic) emptyList() else availableTranslations()
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
              sura,
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

  @VisibleForTesting
  internal fun search(
    query: String,
    translations: List<LocalTranslation>? = null,
    rawArabic: Boolean = false
  ): Cursor? {
    Timber.d("query length: %d", query.length)
    val queryIsArabic = QuranUtils.doesStringContainArabic(query)
    if (queryIsArabic) {
      if (!quranFileUtils.hasArabicSearchDatabase()) {
        return null
      }
      val cursor = if (rawArabic) {
        searchRawArabic(query)
      } else {
        search(query, QURAN_ARABIC_DATABASE, true)
      }
      if (cursor != null && cursor.count > 0) {
        return cursor
      }
      closeCursor(cursor)
      return null
    }

    val resolvedTranslations = translations ?: availableTranslations()
    val total = resolvedTranslations.size
    for (i in 0 until total) {
      val (_, filename, _, _, _, _, languageCode) = resolvedTranslations[i]
      if ("ar" == languageCode) {
        // skip arabic databases always since it's confusing to people for now.
        // in the future, can think of better ways to enable tafseer search.
        continue
      }
      val cursor = search(query, filename, true)
      if (cursor != null && cursor.count > 0) {
        return cursor
      }
      closeCursor(cursor)
    }
    return null
  }

  @VisibleForTesting
  internal open fun search(query: String, databaseName: String, wantSnippets: Boolean): Cursor? {
    val handler = getDatabaseHandler(context!!, databaseName, quranFileUtils)
    return handler.search(query, wantSnippets, QURAN_ARABIC_DATABASE == databaseName)
  }

  private fun searchRawArabic(query: String): Cursor? {
    val handler = getDatabaseHandler(context!!, QURAN_ARABIC_DATABASE, quranFileUtils)
    return handler.searchRawArabic(query)
  }

  /**
   * Searches locally installed tafsir and translation databases without touching
   * [RustArabicSearchEngine], preserving the global Quran verse arena and leaving
   * standard [search] untouched.
   */
  @VisibleForTesting
  internal fun searchTafsir(
    query: String,
    translations: List<LocalTranslation>? = null,
    maxResults: Int = Int.MAX_VALUE
  ): Cursor? {
    val resolvedTranslations = translations ?: availableTranslations()
    if (resolvedTranslations.isEmpty()) {
      return null
    }

    val matrix = MatrixCursor(TAFSIR_COLUMNS)
    val sanitized = sanitizeTafsirQuery(query)
    if (sanitized.isEmpty() || maxResults <= 0) {
      return matrix
    }

    val queryIsArabic = QuranUtils.doesStringContainArabic(sanitized)
    val orderedTranslations = if (queryIsArabic) {
      val arabicFirst = resolvedTranslations.filter { "ar" == it.languageCode }
      val others = resolvedTranslations.filterNot { "ar" == it.languageCode }
      arabicFirst + others
    } else {
      resolvedTranslations
    }

    var totalAdded = 0
    for (translation in orderedTranslations) {
      if (totalAdded >= maxResults) {
        break
      }
      try {
        searchTafsirDatabase(sanitized, translation.filename)?.use { inner ->
          val translatorName = translation.resolveTranslatorName()
          while (inner.moveToNext() && totalAdded < maxResults) {
            val id = inner.getLong(0)
            val sura = inner.getInt(1)
            val ayah = inner.getInt(2)
            val text = inner.getString(3) ?: ""
            matrix.addRow(
              arrayOf<Any>(
                id,
                sura,
                ayah,
                text,
                translatorName,
                translation.id,
                translation.languageCode.orEmpty()
              )
            )
            totalAdded++
          }
        }
      } catch (e: SQLiteException) {
        Timber.w(e, "Tafsir search failed for %s", translation.filename)
      }
    }
    return matrix
  }

  @VisibleForTesting
  internal open fun searchTafsirDatabase(query: String, databaseName: String): Cursor? {
    val handler = getDatabaseHandler(context!!, databaseName, quranFileUtils)
    if (QuranUtils.doesStringContainArabic(query)) {
      // Normalized Arabic tafsir scan (diacritic-insensitive, Alef/Hamza/Ta-Marbuta/Ya folded,
      // unlimited recall with clean context snippets) without touching RustArabicSearchEngine.
      return handler.searchArabicNormalized(query)
    }
    // CRITICAL: Always pass isArabicDatabase = false so DatabaseHandler uses DefaultSearcher
    // and never calls RustArabicSearchEngine.ensureLoaded on a tafsir DB.
    return handler.search(query, withSnippets = true, isArabicDatabase = false)
  }

  private fun sanitizeTafsirQuery(query: String): String {
    val cleaned = buildString(query.length) {
      var prevSpace = true
      for (ch in query) {
        val isFtsOperator = ch == '"' || ch == '*' || ch == '^' ||
          ch == '(' || ch == ')' || ch == ':' ||
          ch == '{' || ch == '}' || ch == '[' || ch == ']' ||
          ch == '-'
        if (isFtsOperator || ch.isWhitespace()) {
          if (!prevSpace) {
            append(' ')
            prevSpace = true
          }
        } else {
          append(ch)
          prevSpace = false
        }
      }
    }.trim()

    if (cleaned.isEmpty()) {
      return ""
    }

    return cleaned.split(' ')
      .filter { token ->
        if (token.isEmpty()) return@filter false
        val upper = token.uppercase(Locale.ROOT)
        upper != "AND" && upper != "OR" && upper != "NOT" &&
          upper != "NEAR" && !upper.startsWith("NEAR/")
      }
      .joinToString(" ")
  }

  override fun getType(uri: Uri): String? {
    return when (uriMatcher.match(uri)) {
      SEARCH_VERSES, SEARCH_TAFSIR -> {
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
    private const val QUERY_PARAM_RAW_ARABIC = "raw"

    @JvmField val SEARCH_URI: Uri = Uri.parse("content://$AUTHORITY/quran/search")
    @JvmField val SEARCH_RAW_ARABIC_URI: Uri =
      SEARCH_URI.buildUpon().appendQueryParameter(QUERY_PARAM_RAW_ARABIC, "1").build()
    @JvmField val SEARCH_TAFSIR_URI: Uri = Uri.parse("content://$AUTHORITY/quran/tafsir/search")
    const val VERSES_MIME_TYPE =
      ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.quran.labs.androidquran"
    const val QURAN_ARABIC_DATABASE = QuranFileConstants.ARABIC_DATABASE
    internal val TAFSIR_COLUMNS = arrayOf(
      BaseColumns._ID,
      "sura",
      "ayah",
      "text",
      "translator_name",
      "translation_id",
      "language_code"
    )

    // UriMatcher stuff
    private const val SEARCH_VERSES = 0
    private const val SEARCH_SUGGEST = 1
    private const val SEARCH_TAFSIR = 2
    private val uriMatcher = buildUriMatcher()

    private fun buildUriMatcher(): UriMatcher {
      val matcher = UriMatcher(UriMatcher.NO_MATCH)
      matcher.addURI(AUTHORITY, "quran/search", SEARCH_VERSES)
      matcher.addURI(AUTHORITY, "quran/search/*", SEARCH_VERSES)
      matcher.addURI(AUTHORITY, "quran/tafsir/search", SEARCH_TAFSIR)
      matcher.addURI(AUTHORITY, "quran/tafsir/search/*", SEARCH_TAFSIR)
      matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST)
      matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST)
      return matcher
    }
  }
}
