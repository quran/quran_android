package com.quran.labs.androidquran.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.SuraAyah
import com.quran.data.source.DisplaySize
import com.quran.data.source.PageProvider
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakePageProvider
import com.quran.labs.androidquran.util.QuranFileUtils
import com.quran.labs.androidquran.util.QuranScreenInfo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AyahInfoDatabaseHandlerTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Before
  fun setUp() {
    AyahInfoDatabaseHandler.clearAyahInfoCache()
  }

  @After
  fun tearDown() {
    AyahInfoDatabaseHandler.clearAyahInfoCache()
  }

  @Test
  fun `getVersesBoundsForPage merges consecutive glyphs on same line and groups by sura ayah`() {
    val quranFileUtils = createQuranFileUtils(hasGlyphData = true)
    val dbDir = quranFileUtils.quranAyahDatabaseDirectory.apply { mkdirs() }
    val dbFile = File(dbDir, "ayahinfo_test.db")
    dbFile.delete()

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL(
        """
        CREATE TABLE glyphs(
          glyph_id INTEGER PRIMARY KEY,
          page_number INTEGER,
          line_number INTEGER,
          sura_number INTEGER,
          ayah_number INTEGER,
          position INTEGER,
          min_x INTEGER,
          min_y INTEGER,
          max_x INTEGER,
          max_y INTEGER,
          glyph_type TEXT
        )
        """.trimIndent()
      )
      db.execSQL("INSERT INTO glyphs VALUES (1, 1, 1, 1, 1, 1, 10, 20, 30, 40, 'word')")
      db.execSQL("INSERT INTO glyphs VALUES (2, 1, 1, 1, 1, 2, 30, 20, 60, 40, 'word')")
      db.execSQL("INSERT INTO glyphs VALUES (3, 1, 2, 1, 1, 3, 15, 45, 50, 65, 'end')")
      db.execSQL("INSERT INTO glyphs VALUES (4, 1, 2, 1, 2, 1, 55, 45, 90, 65, 'word')")
    } finally {
      db.close()
    }

    try {
      val handler = AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler(
        "ayahinfo_test.db",
        quranFileUtils
      )
      assertThat(handler).isNotNull()

      val coords = handler!!.getVersesBoundsForPage(1)
      assertThat(coords.page).isEqualTo(1)
      assertThat(coords.ayahCoordinates.keys).containsExactly("1:1", "1:2")

      val ayah1Bounds = coords.ayahCoordinates["1:1"]!!
      assertThat(ayah1Bounds).hasSize(2)
      assertThat(ayah1Bounds[0].bounds).isEqualTo(RectF(10f, 20f, 60f, 40f))
      assertThat(ayah1Bounds[1].bounds).isEqualTo(RectF(15f, 45f, 50f, 65f))

      val ayah2Bounds = coords.ayahCoordinates["1:2"]!!
      assertThat(ayah2Bounds).hasSize(1)
      assertThat(ayah2Bounds[0].bounds).isEqualTo(RectF(55f, 45f, 90f, 65f))

      assertThat(coords.glyphCoordinates).isNotNull()
      assertThat(coords.glyphCoordinates!!.glyphsByAyah[SuraAyah(1, 1)]).hasSize(3)
      assertThat(coords.glyphCoordinates!!.glyphsByAyah[SuraAyah(1, 2)]).hasSize(1)

      // Without ayah_markers table, getPageInfo caches hasVerseMarkerData = false
      val pageInfo = handler.getPageInfo(1, true)
      assertThat(pageInfo.pageBounds).isEqualTo(RectF(10f, 20f, 90f, 65f))
      assertThat(pageInfo.ayahMarkers).isEmpty()
      assertThat(pageInfo.suraHeaders).isEmpty()
    } finally {
      AyahInfoDatabaseHandler.clearAyahInfoCache()
      dbFile.delete()
    }
  }

  @Test
  fun `getPageInfo returns sura headers and verse markers when ayah_markers table exists`() {
    val quranFileUtils = createQuranFileUtils(hasGlyphData = false)
    val dbDir = quranFileUtils.quranAyahDatabaseDirectory.apply { mkdirs() }
    val dbFile = File(dbDir, "ayahinfo_markers.db")
    dbFile.delete()

    val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    try {
      db.execSQL(
        """
        CREATE TABLE glyphs(
          glyph_id INTEGER PRIMARY KEY,
          page_number INTEGER,
          line_number INTEGER,
          sura_number INTEGER,
          ayah_number INTEGER,
          position INTEGER,
          min_x INTEGER,
          min_y INTEGER,
          max_x INTEGER,
          max_y INTEGER
        )
        """.trimIndent()
      )
      db.execSQL("INSERT INTO glyphs VALUES (1, 2, 1, 2, 1, 1, 5, 10, 100, 50)")
      db.execSQL("CREATE TABLE sura_headers(sura_number INTEGER, page_number INTEGER, x INTEGER, y INTEGER, width INTEGER, height INTEGER)")
      db.execSQL("INSERT INTO sura_headers VALUES (2, 2, 10, 5, 80, 20)")
      db.execSQL("CREATE TABLE ayah_markers(sura_number INTEGER, ayah_number INTEGER, page_number INTEGER, x INTEGER, y INTEGER)")
      db.execSQL("INSERT INTO ayah_markers VALUES (2, 1, 2, 42, 30)")
    } finally {
      db.close()
    }

    try {
      val handler = AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler(
        "ayahinfo_markers.db",
        quranFileUtils
      )
      val pageInfo = handler!!.getPageInfo(2, true)

      assertThat(pageInfo.pageBounds).isEqualTo(RectF(5f, 10f, 100f, 50f))
      assertThat(pageInfo.suraHeaders).hasSize(1)
      assertThat(pageInfo.suraHeaders.single().sura).isEqualTo(2)
      assertThat(pageInfo.ayahMarkers).hasSize(1)
      assertThat(pageInfo.ayahMarkers.single().ayah).isEqualTo(1)
    } finally {
      AyahInfoDatabaseHandler.clearAyahInfoCache()
      dbFile.delete()
    }
  }

  @Test
  fun `getAyahInfoDatabaseHandler rejects path traversal database names`() {
    val quranFileUtils = createQuranFileUtils(hasGlyphData = false)
    quranFileUtils.quranAyahDatabaseDirectory.mkdirs()

    var rejected = false
    try {
      AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler("../escape.db", quranFileUtils)
    } catch (_: IllegalArgumentException) {
      rejected = true
    }
    assertThat(rejected).isTrue()
  }

  private fun createQuranFileUtils(hasGlyphData: Boolean): QuranFileUtils {
    val delegate = FakePageProvider()
    val pageProvider: PageProvider = object : PageProvider by delegate {
      override fun ayahInfoDbHasGlyphData(): Boolean = hasGlyphData
    }
    val display = context.getSystemService(DisplayManager::class.java)
      .getDisplay(Display.DEFAULT_DISPLAY)!!
    val screenInfo = QuranScreenInfo(
      context,
      display,
      pageProvider.getPageSizeCalculator(DisplaySize(0, 0))
    )
    return QuranFileUtils(context, pageProvider, screenInfo)
  }
}
