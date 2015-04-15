package com.quran.labs.androidquran;

import com.quran.labs.androidquran.ui.QuranActionBarActivity;
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment;
import com.quran.labs.androidquran.util.AudioManagerUtils;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;

public class QuranPreferenceActivity extends QuranActionBarActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    ((QuranApplication) getApplication()).refreshLocale(false);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.preferences);

    final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.menu_settings);
    toolbar.setNavigationOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });

    AudioManagerUtils.clearCache();
    getFragmentManager().beginTransaction()
        .replace(R.id.content, new QuranSettingsFragment())
        .commit();
  }

  public void restartActivity() {
    ((QuranApplication) getApplication()).refreshLocale(true);
    Intent intent = getIntent();
    finish();
    startActivity(intent);
  }
}
