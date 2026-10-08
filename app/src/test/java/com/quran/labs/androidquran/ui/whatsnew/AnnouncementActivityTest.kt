package com.quran.labs.androidquran.ui.whatsnew

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import com.google.common.truth.Truth.assertThat
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.QuranActivity
import com.quran.labs.androidquran.util.QuranSettings
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class AnnouncementActivityTest {
  private val context = getApplicationContext<Context>()
  private lateinit var settings: QuranSettings

  @Before
  fun setUp() {
    QuranSettings.setInstance(null)
    settings = QuranSettings.getInstance(context)
  }

  @After
  fun tearDown() {
    QuranSettings.setInstance(null)
  }

  @Test
  fun backAfterRecreationAcknowledgesAndPreservesTheDestination() {
    val destination = Intent(context, QuranActivity::class.java)
      .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      .putExtra(QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, true)
    val intent = AnnouncementActivity.createIntent(context, destination)

    Robolectric.buildActivity(AnnouncementActivity::class.java, intent).use { controller ->
      controller.setup().recreate()
      val activity = controller.get()
      assertThat(AnnouncementActivity.shouldShow(settings)).isTrue()
      assertThat(shadowOf(activity).nextStartedActivity).isNull()

      activity.onBackPressedDispatcher.onBackPressed()

      val launched = shadowOf(activity).nextStartedActivity
      assertThat(launched.component).isEqualTo(destination.component)
      assertThat(launched.flags).isEqualTo(destination.flags)
      assertThat(launched.getBooleanExtra(QuranActivity.EXTRA_SHOW_TRANSLATION_UPGRADE, false)).isTrue()
      assertThat(AnnouncementActivity.shouldShow(settings)).isFalse()
      assertThat(activity.isFinishing).isTrue()
    }

    // A stale launch/restored activity must forward immediately once acknowledged.
    Robolectric.buildActivity(AnnouncementActivity::class.java, intent).use { controller ->
      val activity = controller.create().get()
      assertThat(shadowOf(activity).nextStartedActivity.component).isEqualTo(destination.component)
      assertThat(activity.isFinishing).isTrue()
    }
  }

  @Test
  fun interruptedPresentationRemainsPending() {
    val destination = Intent(context, QuranActivity::class.java)
    val intent = AnnouncementActivity.createIntent(context, destination)
    Robolectric.buildActivity(AnnouncementActivity::class.java, intent).use { controller ->
      controller.setup()
    }

    QuranSettings.setInstance(null)
    assertThat(AnnouncementActivity.shouldShow(QuranSettings.getInstance(context))).isTrue()
  }
}
