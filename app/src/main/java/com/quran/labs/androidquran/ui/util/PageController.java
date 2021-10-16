package com.quran.labs.androidquran.ui.util;

import android.view.MotionEvent;

import com.quran.data.model.SuraAyah;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;

public interface PageController {
  boolean handleTouchEvent(MotionEvent event, AyahSelectedListener.EventType eventType, int page);
  void handleRetryClicked();
  void onScrollChanged(float y);
  void handleLongPress(SuraAyah suraAyah);
  void endAyahMode();
}
