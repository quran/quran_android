package com.quran.labs.androidquran.ui

import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranActivityBackCallbackTest {

  @Test
  fun `back press collapses expanded search view without finishing activity`() {
    ActivityScenario.launch(QuranActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        val searchItem = toolbar.menu.findItem(R.id.search)
        assertThat(searchItem).isNotNull()

        assertThat(searchItem.expandActionView()).isTrue()
        assertThat(searchItem.isActionViewExpanded).isTrue()

        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(searchItem.isActionViewExpanded).isFalse()
        assertThat(activity.isFinishing).isFalse()
      }
    }
  }

  @Test
  fun `back press finishes active support action mode without finishing activity`() {
    ActivityScenario.launch(QuranActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        var destroyed = false
        val mode = activity.startSupportActionMode(object : ActionMode.Callback {
          override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = true
          override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
          override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false
          override fun onDestroyActionMode(mode: ActionMode) {
            destroyed = true
          }
        })
        assertThat(mode).isNotNull()

        activity.onBackPressedDispatcher.onBackPressed()

        assertThat(destroyed).isTrue()
        assertThat(activity.isFinishing).isFalse()
      }
    }
  }
}
