package com.quran.labs.androidquran.ui.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.preference.CheckBoxPreference;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

public class QuranCheckBoxPreference extends CheckBoxPreference {

  public QuranCheckBoxPreference(Context context, AttributeSet attrs,
      int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public QuranCheckBoxPreference(Context context, AttributeSet attrs,
      int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public QuranCheckBoxPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public QuranCheckBoxPreference(Context context) {
    super(context);
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
