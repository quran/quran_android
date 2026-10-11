package com.quran.labs.androidquran

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Looper
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class SearchActivityTest {
  @Test
  fun `search suggestion before migration opens setup without a pending destination`() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("content://search/1"))
      .putExtra(SearchManager.USER_QUERY, "mercy")
    Robolectric.buildActivity(SearchActivity::class.java, intent).use { controller ->
      val activity = controller.create().get()
      shadowOf(Looper.getMainLooper()).idle()
      assertThat(activity.quranSettings.haveMigratedLegacyBookmarksToMobileSync()).isFalse()
      val launched = shadowOf(activity).nextStartedActivity
      assertThat(launched.component?.className).isEqualTo(QuranDataActivity::class.java.name)
      assertThat(launched.action).isNull()
      assertThat(launched.extras).isNull()
      assertThat(activity.isFinishing).isTrue()
    }
  }
}
