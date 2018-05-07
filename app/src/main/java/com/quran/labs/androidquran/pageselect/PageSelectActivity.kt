package com.quran.labs.androidquran.pageselect

import android.os.Bundle
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import com.quran.labs.androidquran.R

class PageSelectActivity : AppCompatActivity() {
  private lateinit var adapter : PageSelectAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.page_select_page)

    adapter = PageSelectAdapter(LayoutInflater.from(this))
    val viewPager = findViewById<ViewPager>(R.id.pager)
    viewPager.adapter = adapter
  }
}
