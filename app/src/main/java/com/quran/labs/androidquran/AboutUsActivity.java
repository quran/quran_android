package com.quran.labs.androidquran;

import com.quran.labs.androidquran.ui.QuranActionBarActivity;
import com.quran.labs.androidquran.ui.fragment.AboutFragment;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

public class AboutUsActivity extends QuranActionBarActivity {

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.about_us);

    final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.menu_about);
    setSupportActionBar(toolbar);
    final ActionBar ab = getSupportActionBar();
    if (ab != null) {
      ab.setDisplayHomeAsUpEnabled(true);
    }

    final FragmentManager fm = getFragmentManager();
    final Fragment fragment = fm.findFragmentById(R.id.content);
    if (fragment == null) {
      fm.beginTransaction()
          .replace(R.id.content, new AboutFragment())
          .commit();
    }
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }
    return false;
  }
}
