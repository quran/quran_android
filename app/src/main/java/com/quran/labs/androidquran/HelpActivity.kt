package com.quran.labs.androidquran

import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams

class HelpActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()

    super.onCreate(savedInstanceState)

    setContentView(R.layout.help)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.menu_help)
    setSupportActionBar(toolbar)
    val ab = supportActionBar
    ab?.setDisplayHomeAsUpEnabled(true)

    val root = findViewById<ViewGroup>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = insets.top
        leftMargin = insets.left
        rightMargin = insets.right
      }

      windowInsets
    }

    val helpWrapper = findViewById<ViewGroup>(R.id.help_wrapper)
    ViewCompat.setOnApplyWindowInsetsListener(helpWrapper) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )

      helpWrapper.setPadding(
        helpWrapper.paddingLeft,
        helpWrapper.paddingTop,
        helpWrapper.paddingRight,
        helpWrapper.paddingBottom + insets.bottom
      )

      windowInsets
    }

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
