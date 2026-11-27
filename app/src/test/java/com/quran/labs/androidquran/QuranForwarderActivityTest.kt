package com.quran.labs.androidquran

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.PagerActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApplication::class, sdk = [33])
class QuranForwarderActivityTest {

  @Test
  fun `invalid sura deep link finishes without launching QuranDataActivity`() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("quran://999/1"))
    val activity = Robolectric.buildActivity(QuranForwarderActivity::class.java, intent)
      .create()
      .get()

    assertThat(activity.isFinishing).isTrue()
    assertThat(Shadows.shadowOf(activity).nextStartedActivity).isNull()
  }

  @Test
  fun `out of bounds ayah deep link finishes without launching QuranDataActivity`() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("quran://1/200"))
    val activity = Robolectric.buildActivity(QuranForwarderActivity::class.java, intent)
      .create()
      .get()

    assertThat(activity.isFinishing).isTrue()
    assertThat(Shadows.shadowOf(activity).nextStartedActivity).isNull()
  }

  @Test
  fun `negative sura deep link finishes without launching QuranDataActivity`() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("quran://-1/1"))
    val activity = Robolectric.buildActivity(QuranForwarderActivity::class.java, intent)
      .create()
      .get()

    assertThat(activity.isFinishing).isTrue()
    assertThat(Shadows.shadowOf(activity).nextStartedActivity).isNull()
  }

  @Test
  fun `valid deep link launches QuranDataActivity with page and highlight extras`() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("quran://2/286"))
    val activity = Robolectric.buildActivity(QuranForwarderActivity::class.java, intent)
      .create()
      .get()

    assertThat(activity.isFinishing).isTrue()
    val started = Shadows.shadowOf(activity).nextStartedActivity
    assertThat(started).isNotNull()
    assertThat(started.component?.className).isEqualTo(QuranDataActivity::class.java.name)
    assertThat(started.getIntExtra("page", -1)).isEqualTo(49)
    assertThat(started.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, -1)).isEqualTo(2)
    assertThat(started.getIntExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, -1)).isEqualTo(286)
  }
}
