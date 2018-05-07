package com.quran.labs.androidquran.pageselect

import android.support.v4.view.PagerAdapter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.quran.labs.androidquran.R

internal class PageSelectAdapter(val inflater: LayoutInflater) : PagerAdapter() {
  private val items : MutableList<PageTypeItem> = mutableListOf()

  fun replaceItems(updates: List<PageTypeItem>) {
    items.clear()
    items.addAll(updates)
    notifyDataSetChanged()
  }

  override fun getCount() = items.size

  override fun isViewFromObject(view: View, obj: Any): Boolean {
    return obj === view
  }

  override fun instantiateItem(container: ViewGroup, position: Int): Any {
    return inflater.inflate(R.layout.page_select_page, container, true)
  }
}
