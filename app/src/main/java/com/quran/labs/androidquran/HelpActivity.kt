package com.quran.labs.androidquran

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat

class HelpActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val actionBar = supportActionBar
    if (actionBar != null) {
      actionBar.setDisplayShowHomeEnabled(true)
      actionBar.setDisplayHomeAsUpEnabled(true)
    }

    setContentView(R.layout.help)

    val helpText = findViewById<TextView>(R.id.txtHelp)
    helpText.text = HtmlCompat.fromHtml(getString(R.string.help), HtmlCompat.FROM_HTML_MODE_COMPACT)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return false
  }

}
