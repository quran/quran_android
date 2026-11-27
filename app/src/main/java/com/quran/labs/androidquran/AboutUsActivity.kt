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
import com.quran.labs.androidquran.ui.fragment.AboutFragment
import com.quran.labs.androidquran.util.ThemeUtil

class AboutUsActivity : AppCompatActivity() {

  public override fun onCreate(savedInstanceState: Bundle?) {
    ThemeUtil.applyActivityTheme(this)
    ThemeUtil.enableThemedEdgeToEdge(this)
    super.onCreate(savedInstanceState)

    setContentView(R.layout.about_us)

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

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    toolbar.setTitle(R.string.menu_about)
    setSupportActionBar(toolbar)
    supportActionBar?.setDisplayHomeAsUpEnabled(true)

    val fragmentManager = supportFragmentManager
    val fragment = fragmentManager.findFragmentById(R.id.content)
    if (fragment == null) {
      fragmentManager.beginTransaction()
        .replace(R.id.content, AboutFragment())
        .commit()
    }
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == android.R.id.home) {
      finish()
      return true
    }
    return false
  }
}
