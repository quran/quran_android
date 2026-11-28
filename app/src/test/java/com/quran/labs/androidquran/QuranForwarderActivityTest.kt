package com.quran.labs.androidquran

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.ui.PagerActivity
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranForwarderActivityTest {

  private lateinit var context: Context

  @Before
  fun setup() {
    context = getApplicationContext()
    Intents.init()
  }

  @After
  fun teardown() {
    Intents.release()
  }

  private fun open(url: String) {
    val intent = Intent(context, QuranForwarderActivity::class.java).apply {
      action = Intent.ACTION_VIEW
      data = Uri.parse(url)
    }
    ActivityScenario.launch<QuranForwarderActivity>(intent).use {}
  }

  private fun assertOpened(sura: Int, ayah: Int) {
    Intents.intended(
      Matchers.allOf(
        IntentMatchers.hasComponent(QuranDataActivity::class.java.name),
        IntentMatchers.hasExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura),
        IntentMatchers.hasExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah)
      )
    )
  }

  @Test
  fun testOpensTheSuraAndAyahInTheLink() {
    open("quran://2/255")
    assertOpened(sura = 2, ayah = 255)
  }

  @Test
  fun testOpensTheFirstAyahWhenOnlyASuraIsGiven() {
    open("quran://36")
    assertOpened(sura = 36, ayah = 1)
  }

  @Test
  fun testKeepsTheAyahWhenTheLinkCarriesAQueryString() {
    open("quran://2/255?play=true")
    assertOpened(sura = 2, ayah = 255)
  }

  @Test
  fun testKeepsTheAyahWhenTheQueryFollowsATrailingSlash() {
    open("quran://2/255/?play=true")
    assertOpened(sura = 2, ayah = 255)
  }
}
