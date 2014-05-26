package com.quran.labs.androidquran.ui.helpers;

import java.util.HashMap;
import java.util.Map;

public class PageScalingData {
  private static PageScalingData sScalingData;
  private static int sScalingWidth, sScalingHeight;
  private static Map<String, PageScalingData> sCache =
      new HashMap<String, PageScalingData>();

  public static PageScalingData getScalingData() {
    return sScalingData;
  }

  public static PageScalingData initialize(
      int imgWidth, int imgHeight, int width, int height) {
    sScalingWidth = width;
    sScalingHeight = height;

    final String key = width + ":" + height;
    sScalingData = sCache.get(key);
    if (sScalingData == null) {
      sScalingData = new PageScalingData(imgWidth, imgHeight, width, height);
      sCache.put(key, sScalingData);
    }
    return sScalingData;
  }

  public static void onSizeChanged(int newWidth, int newHeight) {
    if (newWidth != 0 && newHeight != 0 &&
        (sScalingWidth != newWidth || sScalingHeight != newHeight)) {
      sScalingWidth = newWidth;
      sScalingHeight = newHeight;
      sScalingData = sCache.get(newWidth + ":" + newHeight);
    }
  }

  public float screenRatio, pageRatio, scaledPageHeight, scaledPageWidth,
      widthFactor, heightFactor, offsetX, offsetY;

  public PageScalingData(int imgWidth, int imgHeight, int width, int height) {
    this.screenRatio = (1.0f * height) / (1.0f * width);
    this.pageRatio = (float) (1.0 * imgHeight / imgWidth);
    if (this.screenRatio < this.pageRatio) {
      this.scaledPageHeight = height;
      this.scaledPageWidth = (float) (1.0 * height / imgHeight * imgWidth);
    } else {
      this.scaledPageWidth = width;
      this.scaledPageHeight = (float) (1.0 * width / imgWidth * imgHeight);
    }

    this.widthFactor = this.scaledPageWidth / imgWidth;
    this.heightFactor = this.scaledPageHeight / imgHeight;

    this.offsetX = (width - this.scaledPageWidth) / 2;
    this.offsetY = (height - this.scaledPageHeight) / 2;
  }
}
