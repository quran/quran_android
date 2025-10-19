package com.quran.labs.androidquran.pageselect

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.viewpager.widget.ViewPager
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.QuranDataActivity
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper
import com.quran.labs.androidquran.util.QuranSettings
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PageSelectActivity : AppCompatActivity() {
  @Inject lateinit var presenter : PageSelectPresenter
  @Inject lateinit var quranSettings: QuranSettings

  private lateinit var adapter : PageSelectAdapter
  private lateinit var viewPager: ViewPager

  private val scope = MainScope()
  private var isProcessing = false

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()

    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    setContentView(R.layout.page_select)

    val display = windowManager.defaultDisplay
    val width = QuranDisplayHelper.getWidthKitKat(display)

    adapter = PageSelectAdapter(LayoutInflater.from(this), width) {
      onPageTypeSelected(it)
    }

    viewPager = findViewById(R.id.pager)
    viewPager.adapter = adapter

    ViewCompat.setOnApplyWindowInsetsListener(viewPager) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      viewPager.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        bottomMargin = insets.bottom
        leftMargin = insets.left
        rightMargin = insets.right
      }

      windowInsets
    }

    // let the next and previous pages be slightly visible
    val pageMargin = resources.getDimensionPixelSize(R.dimen.page_margin)
    val pagerPadding = pageMargin * 2
    viewPager.setPadding(pagerPadding, 0, pagerPadding, 0)
    viewPager.clipToPadding = false
    viewPager.pageMargin = pageMargin
  }

  override fun onResume() {
    super.onResume()
    presenter.bind(this)
  }

  override fun onPause() {
    presenter.unbind(this)
    super.onPause()
  }

  override fun onDestroy() {
    adapter.cleanUp()
    scope.cancel()
    super.onDestroy()
  }

  fun onUpdatedData(data: List<PageTypeItem>) {
    adapter.replaceItems(data, viewPager)
  }

  private fun onPageTypeSelected(type: String) {
    val pageType = quranSettings.pageType
    if (pageType != type) {
      isProcessing = true
      scope.launch {
        // migrate the bookmarks
        presenter.migrateBookmarksData(pageType, type)

        // and we can set up our new page type
        quranSettings.pageType = type

        // go back to Quran Data Activity
        val intent = Intent(this@PageSelectActivity, QuranDataActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
      }
    } else {
      finish()
    }
    isProcessing = false
  }
}
