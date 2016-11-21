package com.quran.labs.androidquran.util;

import android.content.Context;
import android.graphics.Point;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranConstants;

import timber.log.Timber;

public class QuranScreenInfo {
  private static QuranScreenInfo sInstance = null;
  private static int sOrientation;

  private int mHeight;
  private int mMaxWidth;
  private PageProvider mPageProvider;

  private QuranScreenInfo(@NonNull Display display) {
    final Point point = new Point();
    display.getSize(point);

    mHeight = point.y;
    mMaxWidth = (point.x > point.y) ? point.x : point.y;
    mPageProvider = QuranConstants.getPageProvider(display);
    Timber.d("initializing with %d and %d", point.y, point.x);
  }

  public static QuranScreenInfo getInstance() {
    return sInstance;
  }

  public static QuranScreenInfo getOrMakeInstance(Context context) {
    if (sInstance == null ||
        sOrientation != context.getResources().getConfiguration().orientation) {
      sInstance = initialize(context);
      sOrientation = context.getResources().getConfiguration().orientation;
    }
    return sInstance;
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
    mPageProvider.setOverrideParameter(overrideParam);
  }

  public int getHeight() {
    return mHeight;
  }

  public String getWidthParam() {
    return "_" + mPageProvider.getWidthParameter();
  }

  public String getTabletWidthParam() {
    return "_" + mPageProvider.getTabletWidthParameter();
  }

  public boolean isTablet(Context context) {
    return context != null && mMaxWidth > 800 && context.getResources()
        .getBoolean(R.bool.is_tablet);
  }

  public static class DefaultPageProvider implements PageProvider {

    private final int mMaxWidth;
    private String mOverrideParam;

    public DefaultPageProvider(@NonNull Display display) {
      final Point point = new Point();
      display.getSize(point);

      mMaxWidth = (point.x > point.y) ? point.x : point.y;
    }

    @Override
    public String getWidthParameter() {
      if (mMaxWidth <= 320) {
        return "320";
      } else if (mMaxWidth <= 480) {
        return "480";
      } else if (mMaxWidth <= 800) {
        return "800";
      } else if (mMaxWidth <= 1280) {
        return "1024";
      } else {
        if (!TextUtils.isEmpty(mOverrideParam)) {
          return mOverrideParam;
        }
        return "1260";
      }
    }

    @Override
    public String getTabletWidthParameter() {
      if ("1260".equals(getWidthParameter())) {
        // for tablet, if the width is more than 1280, use 1260
        // images for both dimens (only applies to new installs)
        return "1260";
      } else {
        int width = mMaxWidth / 2;
        return getBestTabletLandscapeSizeMatch(width);
      }
    }

    @Override
    public void setOverrideParameter(String parameter) {
      mOverrideParam = parameter;
    }

    private String getBestTabletLandscapeSizeMatch(int width) {
      if (width <= 640) {
        return "512";
      } else {
        return "1024";
      }
    }
  }

  public interface PageProvider {
    String getWidthParameter();
    String getTabletWidthParameter();
    void setOverrideParameter(String parameter);
  }
}
