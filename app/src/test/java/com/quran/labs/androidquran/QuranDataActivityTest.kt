package com.quran.labs.androidquran

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.ui.PagerActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranDataActivityTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `jumpToRequestedPage drops invalid sura highlight and clamps invalid page to PAGES_FIRST`() {
    val intent = QuranDataActivity.openPageIntent(
      context = context,
      page = 9999,
      sura = 999,
      ayah = 1
    )
    val activity = Robolectric.buildActivity(QuranDataActivity::class.java, intent)
      .create()
      .get()

    activity.jumpToRequestedPage(intent)

    val started = Shadows.shadowOf(activity).nextStartedActivity
    assertThat(started).isNotNull()
    assertThat(started.component?.className).isEqualTo(PagerActivity::class.java.name)
    assertThat(started.getIntExtra("page", -1)).isEqualTo(Constants.PAGES_FIRST)
    assertThat(started.hasExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA)).isFalse()
    assertThat(started.hasExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH)).isFalse()
  }

  @Test
  fun `jumpToRequestedPage drops out of bounds ayah and falls back to valid page`() {
    val intent = QuranDataActivity.openPageIntent(
      context = context,
      page = 42,
      sura = 1,
      ayah = 200
    )
    val activity = Robolectric.buildActivity(QuranDataActivity::class.java, intent)
      .create()
      .get()

    activity.jumpToRequestedPage(intent)

    val started = Shadows.shadowOf(activity).nextStartedActivity
    assertThat(started).isNotNull()
    assertThat(started.getIntExtra("page", -1)).isEqualTo(42)
    assertThat(started.hasExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA)).isFalse()
    assertThat(started.hasExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH)).isFalse()
  }

  @Test
  fun `jumpToRequestedPage preserves valid sura and ayah`() {
    val intent = QuranDataActivity.openPageIntent(
      context = context,
      page = 42,
      sura = 2,
      ayah = 255
    )
    val activity = Robolectric.buildActivity(QuranDataActivity::class.java, intent)
      .create()
      .get()

    activity.jumpToRequestedPage(intent)

    val started = Shadows.shadowOf(activity).nextStartedActivity
    assertThat(started).isNotNull()
    assertThat(started.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, -1)).isEqualTo(2)
    assertThat(started.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, -1)).isEqualTo(255)
    assertThat(started.getIntExtra("page", -1)).isEqualTo(42)
  }
}
