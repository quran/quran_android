package com.quran.labs.androidquran.ui

import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.view.ActionMode
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.hybrid.contract.HybridNavTab
import com.quran.labs.androidquran.util.QuranSettings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranActivityBackCallbackTest {

  @Before
  fun setup() {
    QuranSettings.getInstance(ApplicationProvider.getApplicationContext()).pageType = "madani"
  }

  @Test
  fun `back press returns from search or bookmarks tab to index without finishing activity`() {
    ActivityScenario.launch(QuranActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.INDEX)

        activity.hubStateHolder.selectNavTab(HybridNavTab.SEARCH)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.SEARCH)

        activity.onBackPressedDispatcher.onBackPressed()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.INDEX)
        assertThat(activity.isFinishing).isFalse()

        activity.hubStateHolder.selectNavTab(HybridNavTab.BOOKMARKS)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.BOOKMARKS)

        activity.onBackPressedDispatcher.onBackPressed()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertThat(activity.hubStateHolder.uiState.value.selectedTab).isEqualTo(HybridNavTab.INDEX)
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
