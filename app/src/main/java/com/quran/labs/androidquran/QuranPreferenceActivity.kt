package com.quran.labs.androidquran

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment
import com.quran.labs.androidquran.util.ThemeUtil

class QuranPreferenceActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    ThemeUtil.applyActivityTheme(this)
    ThemeUtil.enableThemedEdgeToEdge(this)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.preferences)

    val root = findViewById<ViewGroup>(R.id.root)
    val toolbarArea = findViewById<View>(R.id.toolbar_area)
    val navBarScrim = findViewById<View>(R.id.nav_bar_scrim)
    val navBarDivider = findViewById<View>(R.id.nav_bar_divider)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      toolbarArea.updatePadding(top = insets.top)
      navBarScrim.updateLayoutParams<ViewGroup.LayoutParams> {
        height = insets.bottom
      }
      navBarDivider.visibility = if (insets.bottom > 0) View.VISIBLE else View.GONE
      root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
        topMargin = 0
        leftMargin = insets.left
        rightMargin = insets.right
      }

      windowInsets
    }

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.menu_settings)
    setSupportActionBar(toolbar)
    val ab = supportActionBar
    ab?.setDisplayHomeAsUpEnabled(true)

    val fm = supportFragmentManager
    val fragment = fm.findFragmentById(R.id.content)
    if (fragment == null) {
      fm.beginTransaction()
        .replace(R.id.content, QuranSettingsFragment())
        .commit()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  fun restartActivity() {
    val intent = intent
    finish()
    startActivity(intent)
  }

}
