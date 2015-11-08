package com.quran.labs.androidquran.ui.preference;

import com.quran.labs.androidquran.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
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

  private void init() {
    setLayoutResource(R.layout.about_header);
    setSelectable(false);
  }

  @Override
  protected void onBindView(@NonNull View view) {
    super.onBindView(view);
    if (isEnabled()) {
      final TextView tv = (TextView) view.findViewById(android.R.id.title);
      if (tv != null) {
        tv.setTextColor(Color.WHITE);
      }
    }
  }
}
