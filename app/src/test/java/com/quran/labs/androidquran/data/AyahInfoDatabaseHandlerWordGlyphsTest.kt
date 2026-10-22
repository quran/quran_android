package com.quran.labs.androidquran.data

import android.database.sqlite.SQLiteDatabase
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.AyahGlyph.PauseGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.fakes.FakeQuranFileUtils
import com.quran.page.common.data.coordinates.GlyphTypeResolver
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AyahInfoDatabaseHandlerWordGlyphsTest {

  private val quranFileUtils = FakeQuranFileUtils.create(ApplicationProvider.getApplicationContext())

  // 1:1 is entirely on page 1: hizb marker, two words, a waqf mark, a word and the ayah end.
  // 1:2 starts on page 1 (two words) and continues on page 2 (one word and the ayah end).
  private val types = mapOf(SuraAyah(1, 1) to "hwwpwe", SuraAyah(1, 2) to "wwwe")

  private val resolver = object : GlyphTypeResolver {
    override fun glyphTypes(sura: Int, ayah: Int): String? = types[SuraAyah(sura, ayah)]
  }

  @Before
  fun setUp() {
    val directory = quranFileUtils.quranAyahDatabaseDirectory
    directory.mkdirs()
    val file = File(directory, DATABASE)
    file.delete()
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      db.execSQL(
        "CREATE TABLE glyphs(glyph_id int, page_number int, line_number int, sura_number int, " +
          "ayah_number int, position int, min_x int, max_x int, min_y int, max_y int)"
      )
      var id = 0
      fun glyph(page: Int, line: Int, ayah: Int, position: Int, minX: Int, maxX: Int, minY: Int = 0) {
        db.execSQL(
          "INSERT INTO glyphs VALUES (${id++}, $page, $line, 1, $ayah, $position, $minX, $maxX, " +
            "$minY, ${minY + 50})"
        )
      }
      // page 1, line 1 (right to left)
      glyph(1, 1, 1, 1, 900, 950)
      glyph(1, 1, 1, 2, 800, 880)
      glyph(1, 1, 1, 3, 650, 780)
      glyph(1, 1, 1, 4, 640, 645, minY = 5) // waqf mark
      glyph(1, 1, 1, 5, 500, 630)
      glyph(1, 1, 1, 6, 420, 480)
      // page 1, line 2
      glyph(1, 2, 2, 1, 800, 950, minY = 60)
      glyph(1, 2, 2, 2, 600, 780, minY = 60)
      // page 2, line 1
      glyph(2, 1, 2, 3, 700, 950)
      glyph(2, 1, 2, 4, 620, 680)
    }
  }

  @Test
  fun `derives word glyphs when the database has no glyph types`() {
    val coordinates = handler(resolver).getVersesBoundsForPage(1)

    assertThat(coordinates.glyphCoordinates).isNull()
    val glyphs = coordinates.wordGlyphCoordinates!!.glyphsByAyah[SuraAyah(1, 1)]!!.map { it.glyph }
    assertThat(glyphs.filterIsInstance<WordGlyph>().map { it.position to it.wordPosition })
      .containsExactly(2 to 1, 3 to 2, 5 to 3).inOrder()
    assertThat(glyphs.filterIsInstance<PauseGlyph>().map { it.position }).containsExactly(4)
  }

  @Test
  fun `numbers words of an ayah continuing from the previous page by their position in the ayah`() {
    val coordinates = handler(resolver).getVersesBoundsForPage(2)

    val words = coordinates.wordGlyphCoordinates!!.glyphsByAyah[SuraAyah(1, 2)]!!
      .map { it.glyph }
      .filterIsInstance<WordGlyph>()
    assertThat(words.map { it.wordPosition }).containsExactly(3)
  }

  @Test
  fun `masks a word together with the waqf mark that follows it`() {
    val glyphs = handler(resolver).getVersesBoundsForPage(1).wordGlyphCoordinates!!

    val bounds = glyphs.getMaskBounds("1:1:2")!!

    assertThat(bounds).hasSize(1)
    // the word (650..780) and its waqf mark (640..645), expanded to the midpoints with neighbours
    assertThat(bounds[0].right).isEqualTo(790f)
    assertThat(bounds[0].left).isEqualTo(635f)
  }

  @Test
  fun `masking an ayah leaves the hizb and ayah end markers visible`() {
    val glyphs = handler(resolver).getVersesBoundsForPage(1).wordGlyphCoordinates!!

    val bounds = glyphs.getMaskBounds("1:1")!!

    assertThat(bounds).hasSize(1)
    val mask: RectF = bounds[0]
    assertThat(mask.right).isLessThan(900f) // hizb marker starts at 900
    assertThat(mask.left).isGreaterThan(480f) // ayah end marker ends at 480
  }

  @Test
  fun `ignores ayat whose glyph count doesn't match the resolved types`() {
    val wrongLayout = object : GlyphTypeResolver {
      override fun glyphTypes(sura: Int, ayah: Int): String? =
        if (ayah == 1) "wwe" else types[SuraAyah(sura, ayah)]
    }

    val glyphs = handler(wrongLayout, name = "ayahinfo_mismatch.db").getVersesBoundsForPage(1)
      .wordGlyphCoordinates!!

    assertThat(glyphs.glyphsByAyah[SuraAyah(1, 1)]).isNull()
    assertThat(glyphs.glyphsByAyah[SuraAyah(1, 2)]).isNotNull()
  }

  private fun handler(resolver: GlyphTypeResolver, name: String = DATABASE): AyahInfoDatabaseHandler {
    if (name != DATABASE) {
      File(quranFileUtils.quranAyahDatabaseDirectory, DATABASE)
        .copyTo(File(quranFileUtils.quranAyahDatabaseDirectory, name), overwrite = true)
    }
    return AyahInfoDatabaseHandler.getAyahInfoDatabaseHandler(name, quranFileUtils, resolver)!!
  }

  companion object {
    private const val DATABASE = "ayahinfo_wordglyphs_test.db"
  }
}
