package com.quran.labs.androidquran.ui.util;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.ui.helpers.PageScalingData;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImageAyahUtils {
   private static final String TAG =
           "com.quran.labs.androidquran.ui.util.ImageAyahUtils";

   private static QuranAyah getAyahFromKey(String key){
      String[] parts = key.split(":");
      QuranAyah result = null;
      if (parts.length == 2){
         try {
            int sura = Integer.parseInt(parts[0]);
            int ayah = Integer.parseInt(parts[1]);
            result = new QuranAyah(sura, ayah);
         }
         catch (Exception e){}
      }
      return result;
   }

   public static QuranAyah getAyahFromCoordinates(
           Map<String, List<AyahBounds>> coords,
           HighlightingImageView imageView, float xc, float yc) {
      if (coords == null || imageView == null){ return null; }

      float[] pageXY = getPageXY(xc, yc, imageView);
      if (pageXY == null){ return null; }
      float x = pageXY[0];
      float y = pageXY[1];

      int closestLine = -1;
      int closestDelta = -1;

      SparseArray<List<String>> lineAyahs = new SparseArray<List<String>>();
      Set<String> keys = coords.keySet();
      for (String key : keys){
         List<AyahBounds> bounds = coords.get(key);
         if (bounds == null){ continue; }

         for (AyahBounds b : bounds){
            // only one AyahBound will exist for an ayah on a particular line
            int line = b.getLine();
            List<String> items = lineAyahs.get(line);
            if (items == null){
               items = new ArrayList<String>();
            }
            items.add(key);
            lineAyahs.put(line, items);

            if (b.getMaxX() >= x && b.getMinX() <= x &&
                    b.getMaxY() >= y && b.getMinY() <= y){
               return getAyahFromKey(key);
            }

            int delta = Math.min((int)Math.abs(b.getMaxY() - y),
                    (int)Math.abs(b.getMinY() - y));
            if (closestDelta == -1 || delta < closestDelta){
               closestLine = b.getLine();
               closestDelta = delta;
            }
         }
      }

      if (closestLine > -1){
         int leastDeltaX = -1;
         String closestAyah = null;
         List<String> ayat = lineAyahs.get(closestLine);
         if (ayat != null){
            Log.d(TAG, "no exact match, " + ayat.size() + " candidates.");
            for (String ayah : ayat){
               List<AyahBounds> bounds = coords.get(ayah);
               if (bounds == null){ continue; }
               for (AyahBounds b : bounds){
                  if (b.getLine() > closestLine){
                     // this is the last ayah in ayat list
                     break;
                  }

                  if (b.getLine() == closestLine){
                     // if x is within the x of this ayah, that's our answer
                     if (b.getMaxX() >= x && b.getMinX() <= x){
                        return getAyahFromKey(ayah);
                     }

                     // otherwise, keep track of the least delta and return it
                     int delta = Math.min((int)Math.abs(b.getMaxX() - x),
                             (int)Math.abs(b.getMinX() - x));
                     if (leastDeltaX == -1 || delta < leastDeltaX){
                        closestAyah = ayah;
                        leastDeltaX = delta;
                     }
                  }
               }
            }
         }

         if (closestAyah != null){
            Log.d(TAG, "fell back to closest ayah of " + closestAyah);
            return getAyahFromKey(closestAyah);
         }
      }
      return null;
   }

  public static float[] getToolBarPosition(List<AyahBounds> bounds,
      int screenWidth, int screenHeight, int toolBarWidth, int toolBarHeight) {
    float[] result = null;
    final PageScalingData data = PageScalingData.getScalingData();
    final int size = bounds == null ? 0 : bounds.size();
    if (size > 0 && data != null) {
      result = new float[2];
      final AyahBounds first = bounds.get(0);
      AyahBounds chosen = first;
      float y = (first.getMinY() - toolBarHeight) * data.heightFactor;
      if (y < toolBarHeight) {
        // too close to the top, let's move to the bottom
        chosen = bounds.get(size - 1);
        y = data.heightFactor * (chosen.getMaxY() + toolBarHeight);
        if (y > (screenHeight - toolBarHeight)) {
          y = first.getMaxY();
          chosen = first;
        }
      }

      float x = data.heightFactor * (chosen.getMaxX() - toolBarWidth);
      if (x < 0 || x + toolBarWidth > screenWidth) {
        x = data.heightFactor * chosen.getMinX();
        if (x + toolBarWidth > screenWidth) {
          x = (screenWidth - toolBarWidth - data.offsetX) * data.heightFactor;
        }
      }

      result[0] = x;
      result[1] = y;
    }
    return result;
  }

  private static float[] getPageXY(
      float screenX, float screenY, ImageView imageView) {
    PageScalingData scalingData = PageScalingData.getScalingData();
    if (scalingData == null) {
      final Drawable drawable = imageView.getDrawable();
      if (drawable == null) {
        return null;
      }

      // try to re-initialize scaling data from imageview
      scalingData = PageScalingData.initialize(
          drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(),
          imageView.getWidth(), imageView.getHeight());
    }
    float pageX = screenX / scalingData.widthFactor - scalingData.offsetX;
    float pageY = screenY / scalingData.heightFactor - scalingData.offsetY;
    return new float[]{ pageX, pageY };
  }

  public static AyahBounds getYBoundsForHighlight(
      Map<String, List<AyahBounds>> coordinateData, int sura, int ayah) {
    if (coordinateData == null ||
        coordinateData.get(sura + ":" + ayah) == null) {
      return null;
    }

    Integer upperBound = null;
    Integer lowerBound = null;
    for (AyahBounds bounds : coordinateData.get(sura + ":" + ayah)) {
      if (upperBound == null || bounds.getMinY() < upperBound) {
        upperBound = bounds.getMinY();
      }

      if (lowerBound == null || bounds.getMaxY() > lowerBound) {
        lowerBound = bounds.getMaxY();
      }
    }

    AyahBounds yBounds = null;
    if (upperBound != null) {
      yBounds = new AyahBounds(0, 0, 0, upperBound, 0, lowerBound);
    }
    return yBounds;
  }
}
