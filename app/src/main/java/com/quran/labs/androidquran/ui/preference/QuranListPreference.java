package com.quran.labs.androidquran.ui.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.preference.ListPreference;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

public class QuranListPreference extends ListPreference {

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public QuranListPreference(Context context, AttributeSet attrs,
      int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  public QuranListPreference(Context context,
      AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public QuranListPreference(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public QuranListPreference(Context context) {
    super(context);
  }

  @Override
  protected void onBindView(@NonNull View view) {
    super.onBindView(view);
    if (isEnabled()) {
      final TextView title = (TextView) view.findViewById(android.R.id.title);
      if (title != null) {
        title.setTextColor(Color.WHITE);
      }
    }
  }
}
