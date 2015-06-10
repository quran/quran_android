package com.quran.labs.androidquran.util;

import android.graphics.Point;
import android.os.Build;
import android.support.annotation.NonNull;
import android.view.Display;

public class NaskhPageProvider implements QuranScreenInfo.PageProvider {
  private static final double[] sScreenRatios = { 4.0 / 3.0, 16.0 / 10.0, 5.0 / 3.0, 16.0 / 9.0 };

  private final int mRatioIndex;

  public NaskhPageProvider(@NonNull Display display) {
    final Point point = new Point();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      display.getRealSize(point);
    } else {
      try {
        // getRealSize was actually present since 4.0, but was annotated @hide.
        Display.class.getMethod("getRealSize", Point.class).invoke(display, point);
      } catch (Exception e) {
        display.getSize(point);
      }
    }

    mRatioIndex = getScreenRatioIndex(point.x, point.y);
  }

  private int getScreenRatioIndex(int width, int height) {
    double aspectRatio = (double) height / width;
    if (aspectRatio < 1) {
      // getRealSize "size is adjusted based on the current rotation of the display"
      aspectRatio = 1.0 / aspectRatio;
    }

    // pick the closest
    int closest = 0; //keeps the id of the array
    double minDelta = aspectRatio;

    for (int i = 0; i < sScreenRatios.length; i++){
      final double difference = Math.abs(aspectRatio - sScreenRatios[i]);
      if (difference < minDelta){
        closest = i;
        minDelta = difference;
      } else {
        // once minDelta > difference, the difference will only grow since
        // screen ratios is incremental.
        break;
      }
    }
    return closest;
  }

  @Override
  public String getWidthParameter() {
    switch (mRatioIndex) {
      case 0: {
        // 4:3
        return "1536";
      }
      case 1: {
        // 16:10
        return "1280";
      }
      case 2: {
        // 5:3
        return "1227";
      }
      case 3:
      default: {
        // 16:9 and fallback
        return "1152";
      }
    }
  }

  @Override
  public String getTabletWidthParameter() {
    // use the same size for tablet landscape
    return getWidthParameter();
  }

  @Override
  public void setOverrideParameter(String parameter) {
    // override parameter is irrelevant for naskh pages
  }
}
