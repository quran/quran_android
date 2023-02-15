package com.quran.labs.androidquran.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import com.quran.labs.androidquran.ShortcutsActivity
import com.quran.labs.androidquran.base.TestApplication
import com.quran.labs.androidquran.widget.ShowJumpFragmentActivity
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(application = TestApplication::class)
@RunWith(RobolectricTestRunner::class)
class ShortcutsActivityTest {

  private lateinit var context: Context
  private lateinit var shortcutsActivityIntent: Intent

  @Before
  fun setup() {
    context = getApplicationContext()
    shortcutsActivityIntent = Intent(context, ShortcutsActivity::class.java)
    Intents.init()
  }

  @After
  fun teardown() {
    Intents.release()
  }

  @Test
  fun testJumpToLatestShortcutLaunchesQuranActivity() {
    shortcutsActivityIntent.action = ShortcutsActivity.ACTION_JUMP_TO_LATEST

    ActivityScenario.launch<ShortcutsActivity>(shortcutsActivityIntent).use {
      Intents.intended(Matchers.allOf(
          IntentMatchers.hasComponent(QuranActivity::class.java.name),
          IntentMatchers.hasAction(ShortcutsActivity.ACTION_JUMP_TO_LATEST)))
    }
  }

  @Test
  fun testJumpToShortcutLaunchesShowJumpFragmentActivity() {
    shortcutsActivityIntent.action = ShortcutsActivity.ACTION_JUMP_TO

    ActivityScenario.launch<ShortcutsActivity>(shortcutsActivityIntent).use {
      Intents.intended(IntentMatchers.hasComponent(ShowJumpFragmentActivity::class.java.name))
    }
  }
}
