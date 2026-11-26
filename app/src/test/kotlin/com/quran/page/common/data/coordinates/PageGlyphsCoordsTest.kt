package com.quran.page.common.data.coordinates

import android.graphics.RectF
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class PageGlyphsCoordsTest {

  private fun sampleCoords(): PageGlyphsCoords {
    val ayah1 = SuraAyah(1, 1)
    val ayah2 = SuraAyah(1, 2)
    return PageGlyphsCoords(
      page = 1,
      glyphCoords = listOf(
        GlyphCoords(WordGlyph(ayah1, 1, 1), line = 1, bounds = RectF(100f, 10f, 150f, 30f)),
        GlyphCoords(WordGlyph(ayah1, 2, 2), line = 1, bounds = RectF(50f, 10f, 95f, 30f)),
        GlyphCoords(WordGlyph(ayah2, 1, 1), line = 2, bounds = RectF(40f, 50f, 160f, 70f))
      )
    )
  }

  @Test
  fun `getBounds returns cached bounds for ayah and word keys`() {
    val coords = sampleCoords()

    val first = coords.getBounds("1:1", expandVertically = true, expandHorizontally = false)
    val second = coords.getBounds("1:1", expandVertically = true, expandHorizontally = false)
    val horizontal = coords.getBounds("1:1", expandVertically = false, expandHorizontally = true)
    val word = coords.getBounds("1:1:1", expandVertically = false, expandHorizontally = false)

    assertThat(first).hasSize(1)
    assertThat(second).isSameInstanceAs(first)
    assertThat(first!![0]).isEqualTo(RectF(50f, 10f, 150f, 40f))

    assertThat(horizontal).hasSize(1)
    // Line 1 matches lineBounds.right (150 == 150) and lineBounds.left (50 == 50), so expands to pageBounds (40..160)
    assertThat(horizontal!![0]).isEqualTo(RectF(40f, 10f, 160f, 30f))

    assertThat(word).hasSize(1)
    assertThat(word!![0]).isEqualTo(RectF(100f, 10f, 150f, 30f))
    assertThat(coords.getBounds("1:99", expandVertically = false, expandHorizontally = false)).isNull()
  }

  @Test
  fun `getLineAtPoint and getGlyphAtPoint resolve exact and expanded hits`() {
    val coords = sampleCoords()

    assertThat(coords.getLineAtPoint(120f, 20f)).isEqualTo(1)
    assertThat(coords.getLineAtPoint(120f, 35f)).isEqualTo(1) // midpoint Y between 30 and 50 is 40
    assertThat(coords.getLineAtPoint(120f, 45f)).isEqualTo(2)
    assertThat(coords.getLineAtPoint(10f, 20f)).isNull()

    val glyph = coords.getGlyphAtPoint(120f, 20f)
    assertThat(glyph).isEqualTo(WordGlyph(SuraAyah(1, 1), 1, 1))
  }
}
