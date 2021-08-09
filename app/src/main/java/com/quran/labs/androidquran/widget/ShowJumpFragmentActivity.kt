package com.quran.labs.androidquran.widget

import android.content.Intent
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.labs.androidquran.ui.fragment.JumpFragment
import com.quran.labs.androidquran.ui.helpers.JumpDestination
import com.quran.labs.androidquran.util.QuranSettings
import javax.inject.Inject

/**
 * Transparent activity that just shows a [JumpFragment]. Clicking outside or closing the dialog
 * finishes the activity.
 */
class ShowJumpFragmentActivity : AppCompatActivity(), JumpDestination {

  @Inject
  lateinit var settings: QuranSettings

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    (application as QuranApplication).applicationComponent.inject(this)
    supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
  }

  override fun onStart() {
    if (supportFragmentManager.fragments.isEmpty()) {
      supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) = finish()
      }, false)
      JumpFragment().show(supportFragmentManager, JumpFragment.TAG)
    }
    super.onStart()
  }

  override fun jumpTo(page: Int) {
    val i = Intent(this, PagerActivity::class.java)
    i.putExtra("page", page)
    i.putExtra(PagerActivity.EXTRA_JUMP_TO_TRANSLATION, settings.wasShowingTranslation)
    startActivity(i)
  }

  override fun jumpToAndHighlight(page: Int, sura: Int, ayah: Int) {
    val i = Intent(this, PagerActivity::class.java)
    i.putExtra("page", page)
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura)
    i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah)
    startActivity(i)
  }
}
