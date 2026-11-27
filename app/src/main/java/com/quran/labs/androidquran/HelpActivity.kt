package com.quran.labs.androidquran

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.quran.labs.androidquran.util.ThemeUtil

class HelpActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    ThemeUtil.applyActivityTheme(this)
    ThemeUtil.enableThemedEdgeToEdge(this)

    super.onCreate(savedInstanceState)

    setContentView(R.layout.help)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.menu_help)
    setSupportActionBar(toolbar)
    val ab = supportActionBar
    ab?.setDisplayHomeAsUpEnabled(true)

    val root = findViewById<ViewGroup>(R.id.root)
    val toolbarArea = findViewById<View>(R.id.toolbar_area)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      toolbarArea.updatePadding(top = insets.top)
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = 0
        leftMargin = insets.left
        rightMargin = insets.right
      }

      windowInsets
    }

    val helpWrapper = findViewById<ViewGroup>(R.id.help_wrapper)
    val initialBottomPadding = helpWrapper.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(helpWrapper) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )

      helpWrapper.setPadding(
        helpWrapper.paddingLeft,
        helpWrapper.paddingTop,
        helpWrapper.paddingRight,
        initialBottomPadding + insets.bottom
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
