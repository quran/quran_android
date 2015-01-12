package com.quran.labs.androidquran.ui.util;

import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;

import android.view.MotionEvent;

public interface PageController {
  public boolean handleTouchEvent(MotionEvent event,
      AyahSelectedListener.EventType eventType);
  public void handleRetryClicked();
  public void onScrollChanged(int x, int y, int oldx, int oldy);
}
