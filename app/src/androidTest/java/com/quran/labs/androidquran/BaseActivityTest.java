package com.quran.labs.androidquran;

import android.app.Activity;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public abstract class BaseActivityTest<T extends Activity> {

  @Rule
  public IntentsTestRule<T> rule;

  public BaseActivityTest(Class<T> activityClass) {
    rule = new IntentsTestRule<>(activityClass, true, false);
  }
}
