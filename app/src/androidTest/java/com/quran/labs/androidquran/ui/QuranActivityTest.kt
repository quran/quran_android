package com.quran.labs.androidquran.ui

import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.quran.labs.androidquran.base.BaseActivityTest
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QuranActivityTest : BaseActivityTest() {
  @get:Rule
  val rule = ActivityScenarioRule(QuranActivity::class.java)

  @Before
  fun setup() {
    Intents.init()
  }

  @After
  fun teardown() {
    Intents.release()
  }

  @Test
  fun testClickingOnSuraOnListViewNavigatesToSura() {
    //given
    Espresso.onView(ViewMatchers.withText(Matchers.startsWith("Quran")))
        .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

    //when
    Espresso.onView(
        Matchers.allOf(
            ViewMatchers.withText("Surah Al-Fatihah"), ViewMatchers.isCompletelyDisplayed()
        )
    ).perform(ViewActions.click())

    //then
    Intents.intended(IntentMatchers.hasComponent(PagerActivity::class.java.name))
    Espresso.onView(ViewMatchers.withText("Surah Al-Fatihah"))
        .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
  }
}
