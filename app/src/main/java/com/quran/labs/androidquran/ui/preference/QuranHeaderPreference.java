package com.quran.labs.androidquran.ui.preference;

import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import android.util.AttributeSet;
import android.widget.TextView;

public class QuranHeaderPreference extends Preference {

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public QuranHeaderPreference(Context context, AttributeSet attrs,
      int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    init();
  }

  public QuranHeaderPreference(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  public QuranHeaderPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public QuranHeaderPreference(Context context) {
    super(context);
    init();
  }

  @Override
  public void onBindViewHolder(PreferenceViewHolder holder) {
    super.onBindViewHolder(holder);
    if (isEnabled()) {
      final TextView tv = (TextView) holder.findViewById(android.R.id.title);
      if (tv != null) {
        tv.setTextColor(Color.WHITE);
      }
    }
  }

  private void init() {
    setLayoutResource(R.layout.about_header);
    setSelectable(false);
  }
}
