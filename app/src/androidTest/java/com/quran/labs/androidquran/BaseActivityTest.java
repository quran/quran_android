package com.quran.labs.androidquran;

import android.app.Activity;
import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.intent.rule.IntentsTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public abstract class BaseActivityTest<T extends Activity> {

  @Rule
  public IntentsTestRule<T> rule;

  public BaseActivityTest(Class<T> activityClass) {
    rule = new IntentsTestRule<>(activityClass, true, false);
  }
}
