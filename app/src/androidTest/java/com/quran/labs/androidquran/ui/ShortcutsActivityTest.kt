package com.quran.labs.androidquran.ui

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.quran.labs.androidquran.ShortcutsActivity
import com.quran.labs.androidquran.base.BaseActivityTest
import com.quran.labs.androidquran.widget.ShowJumpFragmentActivity
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ShortcutsActivityTest : BaseActivityTest() {

  private lateinit var shortcutsActivityIntent: Intent
  private lateinit var context: Context

  @Before
  fun setup() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
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
