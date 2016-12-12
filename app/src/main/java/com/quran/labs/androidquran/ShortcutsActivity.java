package com.quran.labs.androidquran;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import com.quran.labs.androidquran.ui.QuranActivity;

public class ShortcutsActivity extends AppCompatActivity {
  public static final String ACTION_JUMP_TO_LATEST = "com.quran.labs.androidquran.last_page";

  private static final String JUMP_TO_LATEST_SHORTCUT_NAME = "lastPage";

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final Intent shortcutIntent = getIntent();
    final String action = shortcutIntent == null ? null : shortcutIntent.getAction();

    Intent intent = new Intent(this, QuranActivity.class);
    if (ACTION_JUMP_TO_LATEST.equals(action)) {
      intent.setAction(action);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        recordShortcutUsage(JUMP_TO_LATEST_SHORTCUT_NAME);
      }
    }
    finish();
    startActivity(intent);
  }

  @TargetApi(Build.VERSION_CODES.N_MR1)
  private void recordShortcutUsage(String shortcut) {
    ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
    shortcutManager.reportShortcutUsed(shortcut);
  }
}
