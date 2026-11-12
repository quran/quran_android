package com.quran.labs.androidquran.view

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.data.model.AyahGlyph.AyahEndGlyph
import com.quran.data.model.AyahGlyph.WordGlyph
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightType
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.helpers.AyahHighlight
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.SingleAyahHighlight
import com.quran.labs.androidquran.ui.helpers.AyahHighlight.TransitionAyahHighlight
import com.quran.labs.androidquran.ui.helpers.HighlightTypes
import com.quran.page.common.data.AyahBounds
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.data.coordinates.GlyphCoords
import com.quran.page.common.data.coordinates.PageGlyphsCoords
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.SortedMap
import java.util.TreeMap

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class HighlightsDrawerTest {

  private val first = SuraAyah(1, 1)
  private val second = SuraAyah(1, 2)

  // a page without glyph types, so its word glyphs are derived (as on Madani and Tajweed pages)
  private val ayahCoordinates = AyahCoordinates(
    page = 1,
    ayahCoordinates = emptyMap(),
    glyphCoordinates = null,
    wordGlyphCoordinates = PageGlyphsCoords(
      1,
      listOf(
        GlyphCoords(WordGlyph(first, 1, 1), 1, RectF(800f, 0f, 900f, 50f)),
        GlyphCoords(AyahEndGlyph(first, 2), 1, RectF(750f, 0f, 790f, 50f)),
        GlyphCoords(WordGlyph(second, 1, 1), 1, RectF(600f, 0f, 740f, 50f)),
        GlyphCoords(AyahEndGlyph(second, 2), 1, RectF(550f, 0f, 590f, 50f)),
      )
    )
  )

  @Test
  fun `audio moving to the next ayah draws its transition`() {
    // while the audio highlight floats from 1:1 to 1:2, its key is "1:1->1:2" (two colons)
    val transition = TransitionAyahHighlight(SingleAyahHighlight(first.sura, first.ayah), SingleAyahHighlight(second.sura, second.ayah))
    val highlights: SortedMap<HighlightType, Set<AyahHighlight>> = TreeMap()
    highlights[HighlightTypes.AUDIO] = setOf(transition)
    val canvas = RecordingCanvas()
    val drawer = HighlightsDrawer(
      { ayahCoordinates },
      { mapOf(transition to listOf(AyahBounds(1, 1, 600, 0, 900, 50))) },
      { highlights },
      {}
    )

    drawer.draw(
      PageCoordinates(1, RectF(0f, 0f, 1000f, 100f), emptyList(), emptyList()),
      canvas,
      ImageView(ApplicationProvider.getApplicationContext())
    )

    // drawn with the transition's own (animated) bounds
    assertThat(canvas.rects).containsExactly(RectF(600f, 0f, 900f, 50f))
  }

  private class RecordingCanvas : Canvas(Bitmap.createBitmap(1000, 100, Bitmap.Config.ARGB_8888)) {
    val rects = mutableListOf<RectF>()

    override fun drawRect(rect: RectF, paint: android.graphics.Paint) {
      rects += RectF(rect)
    }
  }
}
