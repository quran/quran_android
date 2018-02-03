package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.view.Display;
import android.view.WindowManager;

import com.quran.data.page.provider.PageProvider;
import com.quran.labs.androidquran.data.QuranDataModule;

import timber.log.Timber;

public class QuranScreenInfo {
  private static QuranScreenInfo instance = null;
  private static int orientation;

  private int height;
  private int maxWidth;
  private PageProvider pageProvider;

  private QuranScreenInfo(@NonNull Display display) {
    final Point point = new Point();
    display.getSize(point);

    height = point.y;
    maxWidth = (point.x > point.y) ? point.x : point.y;
    pageProvider = QuranDataModule.provideQuranPageProvider(display);
    Timber.d("initializing with %d and %d", point.y, point.x);
  }

  public static QuranScreenInfo getInstance() {
    return instance;
  }

  public static QuranScreenInfo getOrMakeInstance(Context context) {
    if (instance == null ||
        orientation != context.getResources().getConfiguration().orientation) {
      instance = initialize(context);
      orientation = context.getResources().getConfiguration().orientation;
    }
    return instance;
  }

  private static QuranScreenInfo initialize(Context context) {
    final WindowManager w = (WindowManager) context
        .getSystemService(Context.WINDOW_SERVICE);
    final Display display = w.getDefaultDisplay();
    QuranScreenInfo qsi = new QuranScreenInfo(display);
    qsi.setOverrideParam(QuranSettings.getInstance(context).getDefaultImagesDirectory());
    return qsi;
  }

  public void setOverrideParam(String overrideParam) {
    pageProvider.setOverrideParameter(overrideParam);
  }

  public int getHeight() {
    return height;
  }

  public String getWidthParam() {
    return "_" + pageProvider.getWidthParameter();
  }

  public String getTabletWidthParam() {
    return "_" + pageProvider.getTabletWidthParameter();
  }

  public boolean isDualPageMode(Context context) {
    return context != null && maxWidth > 800;
  }
}
