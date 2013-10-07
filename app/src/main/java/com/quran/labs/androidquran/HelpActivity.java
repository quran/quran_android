package com.quran.labs.androidquran;

import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.MenuItem;

public class HelpActivity extends SherlockActivity {
  public void onCreate(Bundle savedInstanceState) {
    setTheme(R.style.Theme_Sherlock);
    super.onCreate(savedInstanceState);

    getSupportActionBar().setDisplayShowHomeEnabled(true);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    setContentView(R.layout.help);

    TextView helpText = (TextView) findViewById(R.id.txtHelp);
    helpText.setText(Html.fromHtml(getString(R.string.help)));
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
