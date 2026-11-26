package com.quran.labs.androidquran.data

import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import androidx.annotation.VisibleForTesting
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.AyahMarkerLocation
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.data.SuraHeaderLocation
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import java.io.File
import java.io.IOException

class AyahInfoDatabaseHandler private constructor(
  databaseName: String,
  quranFileUtils: QuranFileUtils
) {
  private val database: SQLiteDatabase
  private val hasGlyphData: Boolean
  private val hasVerseMarkerData: Boolean
  private val hasGlyphColumn: Boolean

  init {
    val base = quranFileUtils.quranAyahDatabaseDirectory
    val path = File(base, databaseName)
    database = try {
      val baseCanonical = base.canonicalPath
      val basePrefix = if (baseCanonical.endsWith(File.separator)) {
        baseCanonical
      } else {
        baseCanonical + File.separator
      }
      val targetCanonical = path.canonicalPath
      if (databaseName.contains('/') ||
        databaseName.contains('\\') ||
        !targetCanonical.startsWith(basePrefix)
      ) {
        throw IllegalArgumentException("Invalid database name: $databaseName")
      }
      SQLiteDatabase.openDatabase(
        targetCanonical,
        null,
        SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
      ).also { configureReadOnlyCacheSize(it) }
    } catch (e: IOException) {
      throw SQLException("Failed to resolve database path", e)
    }
    hasGlyphData = quranFileUtils.ayahInfoDbHasGlyphData
    hasVerseMarkerData = haveVerseMarkerData()
    hasGlyphColumn = hasGlyphData && haveGlyphData()
  }

  private fun validDatabase(): Boolean = database.isOpen

  fun getPageInfo(page: Int, wantBounds: Boolean): PageCoordinates {
    val bounds = if (wantBounds) getPageBounds(page) else EMPTY_BOUNDS
    return if (hasVerseMarkerData) {
      PageCoordinates(
        page,
        bounds,
        getSuraHeadersForPage(page),
        getVerseMarkersForPage(page)
      )
    } else {
      PageCoordinates(page, bounds, emptyList(), emptyList())
    }
  }

  private fun getPageBounds(page: Int): RectF {
    val colNames = arrayOf(
      "MIN($MIN_X)", "MIN($MIN_Y)",
      "MAX($MAX_X)", "MAX($MAX_Y)"
    )
    return database.query(
      GLYPHS_TABLE, colNames, "$COL_PAGE = ?",
      arrayOf(page.toString()), null, null, null
    ).use { cursor ->
      if (cursor.moveToFirst()) {
        RectF(
          cursor.getInt(0).toFloat(),
          cursor.getInt(1).toFloat(),
          cursor.getInt(2).toFloat(),
          cursor.getInt(3).toFloat()
        )
      } else {
        throw IllegalArgumentException("getPageBounds() on a non-existent page: $page")
      }
    }
  }

  fun getVersesBoundsForPage(page: Int): AyahCoordinates {
    val includeGlyphData = hasGlyphColumn
    val glyphsBuilder = GlyphsBuilder()
    val ayahBounds = HashMap<String, MutableList<AyahBounds>>()
    var lastSura = -1
    var lastAyah = -1
    var lastBounds: MutableList<AyahBounds>? = null

    getVersesBoundsCursorForPage(page, includeGlyphData).use { cursor ->
      while (cursor.moveToNext()) {
        val line = cursor.getInt(1)
        val sura = cursor.getInt(2)
        val ayah = cursor.getInt(3)
        val position = cursor.getInt(4)
        val minX = cursor.getInt(5)
        val minY = cursor.getInt(6)
        val maxX = cursor.getInt(7)
        val maxY = cursor.getInt(8)

        if (includeGlyphData) {
          val glyphType = cursor.getString(9)
          val rect = RectF(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
          glyphsBuilder.append(sura, ayah, position, line, glyphType, rect)
        }

        val bounds = if (sura == lastSura && ayah == lastAyah && lastBounds != null) {
          lastBounds
        } else {
          val key = "$sura:$ayah"
          val existing = ayahBounds.getOrPut(key) { ArrayList() }
          lastSura = sura
          lastAyah = ayah
          lastBounds = existing
          existing
        }

        val last = bounds.lastOrNull()
        val bound = AyahBounds(line, position, minX, minY, maxX, maxY)
        if (last != null && last.line == bound.line) {
          last.engulf(bound)
        } else {
          bounds.add(bound)
        }
      }
    }

    val glyphCoords = if (includeGlyphData) {
      PageGlyphsCoords(page, glyphsBuilder.build())
    } else {
      null
    }

    return AyahCoordinates(page, ayahBounds, glyphCoords)
  }

  private fun haveVerseMarkerData(): Boolean {
    return database.rawQuery(
      "SELECT count(1) from sqlite_master WHERE name = ?",
      arrayOf("ayah_markers")
    ).use { cursor ->
      cursor.moveToFirst() && cursor.getInt(0) > 0
    }
  }

  private fun haveGlyphData(): Boolean {
    return try {
      database.query(
        GLYPHS_TABLE,
        arrayOf(GLYPH_TYPE), null, null, null, null, null, "1"
      ).use { cursor ->
        cursor.moveToFirst() && cursor.getString(0) != null
      }
    } catch (_: Exception) {
      // we don't have glyph data (the column doesn't exist)
      false
    }
  }

  private fun getVerseMarkersForPage(page: Int): List<AyahMarkerLocation> {
    val markers = ArrayList<AyahMarkerLocation>()
    database.query(
      "ayah_markers",
      arrayOf("sura_number", "ayah_number", "x", "y"),
      "page_number = ?", arrayOf(page.toString()), null,
      null, "sura_number, ayah_number ASC"
    ).use { cursor ->
      while (cursor.moveToNext()) {
        markers.add(
          AyahMarkerLocation(
            cursor.getInt(0),
            cursor.getInt(1),
            cursor.getInt(2),
            cursor.getInt(3)
          )
        )
      }
    }
    return markers
  }

  private fun getSuraHeadersForPage(page: Int): List<SuraHeaderLocation> {
    val headers = ArrayList<SuraHeaderLocation>()
    database.query(
      "sura_headers",
      arrayOf("sura_number", "x", "y", "width", "height"),
      "page_number = ?", arrayOf(page.toString()), null,
      null, "sura_number ASC"
    ).use { cursor ->
      while (cursor.moveToNext()) {
        headers.add(
          SuraHeaderLocation(
            cursor.getInt(0),
            cursor.getInt(1),
            cursor.getInt(2),
            cursor.getInt(3),
            cursor.getInt(4)
          )
        )
      }
    }
    return headers
  }

  private fun getVersesBoundsCursorForPage(page: Int, withGlyphData: Boolean): Cursor {
    val columns = if (withGlyphData) {
      arrayOf(COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y, GLYPH_TYPE)
    } else {
      arrayOf(COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y)
    }

    return database.query(
      GLYPHS_TABLE,
      columns,
      "$COL_PAGE = ?",
      arrayOf(page.toString()), null, null,
      "$COL_SURA,$COL_AYAH,$COL_POSITION"
    )
  }

  companion object {
    private val EMPTY_BOUNDS = RectF()
    private const val COL_PAGE = "page_number"
    private const val COL_LINE = "line_number"
    private const val COL_SURA = "sura_number"
    private const val COL_AYAH = "ayah_number"
    private const val COL_POSITION = "position"
    private const val MIN_X = "min_x"
    private const val MIN_Y = "min_y"
    private const val MAX_X = "max_x"
    private const val MAX_Y = "max_y"
    private const val GLYPH_TYPE = "glyph_type"
    private const val GLYPHS_TABLE = "glyphs"

    private val ayahInfoCache = HashMap<String, AyahInfoDatabaseHandler>()
    private var quranDatabaseDirectory: File? = null

    @JvmStatic
    @Synchronized
    internal fun getAyahInfoDatabaseHandler(
      databaseName: String,
      quranFileUtils: QuranFileUtils
    ): AyahInfoDatabaseHandler? {
      val currentAyahDatabaseDirectory = quranFileUtils.quranAyahDatabaseDirectory
      if (currentAyahDatabaseDirectory != quranDatabaseDirectory) {
        quranDatabaseDirectory = currentAyahDatabaseDirectory
        clearAyahInfoCache()
      }

      var handler = ayahInfoCache[databaseName]
      if (handler == null) {
        try {
          val db = AyahInfoDatabaseHandler(databaseName, quranFileUtils)
          if (db.validDatabase()) {
            ayahInfoCache[databaseName] = db
            handler = db
          }
        } catch (_: SQLException) {
          // it's okay, we'll try again later
        }
      }
      return handler
    }

    @VisibleForTesting
    @JvmStatic
    @Synchronized
    internal fun clearAyahInfoCache() {
      for (handler in ayahInfoCache.values) {
        try {
          handler.database.close()
        } catch (_: Exception) {
          // ignore
        }
      }
      ayahInfoCache.clear()
    }

    private fun configureReadOnlyCacheSize(db: SQLiteDatabase) {
      if (!db.isOpen) {
        return
      }
      try {
        db.execSQL("PRAGMA cache_size = -512")
      } catch (_: Exception) {
        // keep default cache size if PRAGMA is unsupported
      }
    }
  }
}
