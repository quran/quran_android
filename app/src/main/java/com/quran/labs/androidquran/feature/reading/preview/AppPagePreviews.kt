package com.quran.labs.androidquran.feature.reading.preview

import android.annotation.SuppressLint
import android.app.Activity
import android.view.MotionEvent
import android.view.View
import androidx.annotation.StringRes
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quran.data.core.QuranInfo
import com.quran.data.di.AppScope
import com.quran.data.model.AyahGlyph
import com.quran.data.model.SuraAyah
import com.quran.data.model.highlight.HighlightType
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.common.Response
import com.quran.labs.androidquran.data.QuranDisplayData
import com.quran.labs.androidquran.model.quran.CoordinatesModelInterface
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper
import com.quran.labs.androidquran.ui.helpers.QuranPageLoaderInterface
import com.quran.labs.androidquran.ui.util.ImageAyahUtils
import com.quran.labs.androidquran.ui.util.PageController
import com.quran.labs.androidquran.util.QuranSettings
import com.quran.labs.androidquran.util.QuranUtils
import com.quran.labs.androidquran.view.QuranImagePageLayout
import com.quran.page.common.data.AyahCoordinates
import com.quran.page.common.data.PageCoordinates
import com.quran.page.common.draw.ImageDrawHelper
import com.quran.recitation.pages.PagePreview
import com.quran.recitation.pages.PagePreviews
import com.quran.recitation.pages.PageRenderer
import com.quran.recitation.presenter.RecitationHighlightsPresenter.HighlightAction
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

/**
 * Mushaf pages outside the reading screen (e.g. a recorded session played back on its pages),
 * loaded and drawn as [com.quran.labs.androidquran.ui.fragment.QuranPageFragment] draws them.
 */
@ContributesBinding(AppScope::class)
class AppPagePreviews @Inject constructor(
  private val quranSettings: QuranSettings,
  private val quranInfo: QuranInfo,
  private val quranDisplayData: QuranDisplayData,
  private val imageDrawHelpers: Set<ImageDrawHelper>,
) : PagePreviews {

  override fun open(activity: Activity): PageRenderer {
    // the activity's own graph: its image width, as the reading screen's
    val component = (activity.application as QuranApplication).applicationComponent
      .activityComponentFactory()
      .generate(activity)
    return Renderer(activity, component.quranPageLoader(), component.coordinatesModel())
  }

  private inner class Renderer(
    private val activity: Activity,
    private val loader: QuranPageLoaderInterface,
    private val coordinates: CoordinatesModelInterface,
  ) : PageRenderer {
    private val open = LinkedHashSet<Preview>()

    override val isNightMode: Boolean get() = quranSettings.isNightMode

    override fun page(page: Int, onTapped: (ayah: SuraAyah, word: Int?) -> Unit): PagePreview =
      Preview(activity, page, loader, coordinates, onTapped) { open -= it }.also { open += it }

    override fun close() {
      open.toList().forEach { it.release() }
    }
  }

  private inner class Preview(
    private val activity: Activity,
    private val page: Int,
    private val loader: QuranPageLoaderInterface,
    private val coordinatesModel: CoordinatesModelInterface,
    private val onTapped: (SuraAyah, Int?) -> Unit,
    private val onReleased: (Preview) -> Unit,
  ) : PagePreview, PageController {
    // fits the page in whatever room it has, in landscape too (no scrolling)
    private val layout = object : QuranImagePageLayout(activity) {
      override fun shouldWrapWithScrollView(): Boolean = false
    }
    private val image = layout.getImageView()
    private val loading = CompositeDisposable()
    private var ayahCoordinates: AyahCoordinates? = null
    // what should be highlighted, by type (word -1: a whole ayah)
    private val wanted = LinkedHashMap<HighlightType, MutableSet<Triple<Int, Int, Int>>>()

    override val view: View get() = layout

    init {
      layout.setPageController(this, page, quranInfo.skip)
      layout.updateView(quranSettings)
      // not at the top of a window: no room to keep for a display cutout
      ViewCompat.setOnApplyWindowInsetsListener(layout) { _, _ -> WindowInsetsCompat.CONSUMED }
      loadImage()
      loadCoordinates()
    }

    private fun loadImage() {
      loading.add(
        loader.loadPages(arrayOf(page))
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(
            { response ->
              val bitmap = response.bitmap
              if (bitmap != null) {
                layout.hideError()
                image.setImageDrawable(bitmap.toDrawable(activity.resources))
              } else {
                layout.showError(errorMessage(response.errorCode))
              }
            },
            { layout.showError(R.string.download_error_general) },
          )
      )
    }

    private fun loadCoordinates() {
      loading.add(
        coordinatesModel.getPageCoordinates(quranSettings.shouldOverlayPageInfo(), page)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe(::setPageCoordinates, {}, ::loadAyahCoordinates)
      )
    }

    private fun loadAyahCoordinates() {
      loading.add(
        coordinatesModel.getAyahCoordinates(page)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribe({ coordinates ->
            ayahCoordinates = coordinates
            image.setAyahData(coordinates)
            // now that the words' places are known
            wanted.keys.toList().forEach { redraw(it) }
            image.invalidate()
          }, {})
      )
    }

    /** As the reading screen's page (AyahImageTrackerItem.onSetPageBounds). */
    private fun setPageCoordinates(pageCoordinates: PageCoordinates) {
      val pageBounds = pageCoordinates.pageBounds
      if (!pageBounds.isEmpty) {
        image.setPageBounds(pageBounds)
        image.setOverlayText(
          quranDisplayData.getSuraNameFromPage(activity, page, true),
          quranDisplayData.getJuzDisplayStringForPage(activity, page),
          QuranUtils.getLocalizedNumber(page),
          QuranDisplayHelper.displayRub3(activity, quranInfo, page),
          quranDisplayData.getManzilForPage(activity, page),
        )
      }
      // without it nothing is highlighted
      image.setPageData(pageCoordinates, imageDrawHelpers)
      image.invalidate()
    }

    override fun apply(actions: List<HighlightAction>) {
      val changed = LinkedHashSet<HighlightType>()
      for (action in actions) {
        when (action) {
          is HighlightAction.Highlight -> with(action.highlightInfo) {
            wanted.getOrPut(highlightType) { LinkedHashSet() } += Triple(sura, ayah, word)
            changed += highlightType
          }
          is HighlightAction.Unhighlight -> with(action.highlightInfo) {
            wanted[highlightType]?.remove(Triple(sura, ayah, word))
            changed += highlightType
          }
          is HighlightAction.UnhighlightAll -> {
            wanted.remove(action.highlightType)
            changed += action.highlightType
          }
        }
      }
      changed.forEach { redraw(it) }
      image.invalidate()
    }

    /** Draws what is wanted of [type] (a word the page can't place is drawn as its ayah). */
    private fun redraw(type: HighlightType) {
      image.unHighlight(type)
      val coordinates = ayahCoordinates ?: return
      val glyphs = coordinates.glyphCoordinates ?: coordinates.wordGlyphCoordinates
      wanted[type]?.forEach { (sura, ayah, word) ->
        val placed = word > 0 && glyphs?.getBoundsForWord(SuraAyah(sura, ayah), word)?.isNotEmpty() == true
        image.highlightAyah(sura, ayah, if (placed) word else -1, type)
      }
    }

    override fun release() {
      loading.dispose()
      wanted.keys.forEach { image.unHighlight(it) }
      wanted.clear()
      image.setImageDrawable(null)
      onReleased(this)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun handleTouchEvent(event: MotionEvent, eventType: AyahSelectedListener.EventType, page: Int): Boolean {
      if (eventType != AyahSelectedListener.EventType.SINGLE_TAP) return false
      val coordinates = ayahCoordinates ?: return false
      val ayah = ImageAyahUtils.getAyahFromCoordinates(coordinates.ayahCoordinates, image, event.x, event.y) ?: return false
      val glyphs = coordinates.glyphCoordinates ?: coordinates.wordGlyphCoordinates
      val word = ImageAyahUtils.getPageXY(event.x, event.y, image)
        ?.let { (x, y) -> glyphs?.getGlyphAtPoint(x, y) as? AyahGlyph.WordGlyph }
        ?.takeIf { it.ayah == ayah }
        ?.wordPosition
      onTapped(ayah, word)
      return true
    }

    override fun handleRetryClicked() {
      layout.hideError()
      loadImage()
    }

    override fun onScrollChanged(y: Float) {}

    override fun handleLongPress(suraAyah: SuraAyah) {}

    override fun endAyahMode() {}
  }

  @StringRes
  private fun errorMessage(errorCode: Int): Int = when (errorCode) {
    Response.ERROR_SD_CARD_NOT_FOUND -> R.string.sdcard_error
    Response.ERROR_NO_INTERNET, Response.ERROR_DOWNLOADING_ERROR -> R.string.download_error_network
    else -> R.string.download_error_general
  }
}
