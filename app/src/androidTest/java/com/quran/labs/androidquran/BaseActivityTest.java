package com.quran.labs.androidquran;

import android.app.Activity;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(AndroidJUnit4.class)
public abstract class BaseActivityTest<T extends Activity> {

  @Rule
  public ActivityTestRule<T> rule;

  @Before
  public void setup() {
    resetData();
  }

  public BaseActivityTest(
      Class<T> activityClass) {
    rule = new ActivityTestRule<>(activityClass, true, false);
  }

  private void resetData() {
    File root = InstrumentationRegistry.getTargetContext().getFilesDir().getParentFile();
    String[] sharedPreferencesFileNames = new File(root, "shared_prefs").list();
    for (String fileName : sharedPreferencesFileNames) {
      InstrumentationRegistry.getTargetContext()
          .getSharedPreferences(fileName.replace(".xml", ""), Context.MODE_PRIVATE).edit().clear()
          .commit();
    }
  }
}
