package com.quran.labs.androidquran;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.quran.labs.androidquran.ui.QuranActionBarActivity;
import com.quran.labs.androidquran.ui.fragment.QuranSettingsFragment;
import com.quran.labs.androidquran.util.AudioManagerUtils;

public class QuranPreferenceActivity extends QuranActionBarActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    ((QuranApplication) getApplication()).refreshLocale(this, false);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.preferences);

    final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.menu_settings);
    setSupportActionBar(toolbar);
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setDisplayHomeAsUpEnabled(true);
    }

    AudioManagerUtils.clearCache();


    final FragmentManager fm = getFragmentManager();
    final Fragment fragment = fm.findFragmentById(R.id.content);
    if (fragment == null) {
      fm.beginTransaction()
          .replace(R.id.content, new QuranSettingsFragment())
          .commit();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {

    super.onSaveInstanceState(outState);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void restartActivity() {
    ((QuranApplication) getApplication()).refreshLocale(this, true);
    Intent intent = getIntent();
    finish();
    startActivity(intent);
  }

}
