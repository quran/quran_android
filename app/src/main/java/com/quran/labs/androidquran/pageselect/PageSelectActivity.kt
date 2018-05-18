package com.quran.labs.androidquran.pageselect

import android.os.Bundle
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import javax.inject.Inject

class PageSelectActivity : AppCompatActivity() {
  @Inject lateinit var presenter : PageSelectPresenter
  private lateinit var adapter : PageSelectAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)

    setContentView(R.layout.page_select)

    adapter = PageSelectAdapter(LayoutInflater.from(this))
    val viewPager = findViewById<ViewPager>(R.id.pager)
    viewPager.adapter = adapter
  }

  override fun onResume() {
    super.onResume()
    presenter.bind(this)
  }

  override fun onPause() {
    presenter.unbind(this)
    super.onPause()
  }

  fun onUpdatedData(data: List<PageTypeItem>) {
    adapter.replaceItems(data)
  }
}
