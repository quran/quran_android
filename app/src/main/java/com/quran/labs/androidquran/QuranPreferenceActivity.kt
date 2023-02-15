package com.quran.labs.androidquran

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment

class QuranPreferenceActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    (application as QuranApplication).refreshLocale(this, false)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.preferences)

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
    (application as QuranApplication).refreshLocale(this, true)
    val intent = intent
    finish()
    startActivity(intent)
  }

}
