package com.quran.labs.androidquran.ui.util;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImageAyahUtils {
   private static final String TAG = ImageAyahUtils.class.getSimpleName();

   private static QuranAyah getAyahFromKey(String key){
      String[] parts = key.split(":");
      QuranAyah result = null;
      if (parts.length == 2){
         try {
            int sura = Integer.parseInt(parts[0]);
            int ayah = Integer.parseInt(parts[1]);
            result = new QuranAyah(sura, ayah);
         }
         catch (Exception e){
           // no op
         }
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

      final SparseArray<List<String>> lineAyahs = new SparseArray<>();
      final Set<String> keys = coords.keySet();
      for (String key : keys){
         List<AyahBounds> bounds = coords.get(key);
         if (bounds == null){ continue; }

         for (AyahBounds b : bounds){
            // only one AyahBound will exist for an ayah on a particular line
            int line = b.getLine();
            List<String> items = lineAyahs.get(line);
            if (items == null){
               items = new ArrayList<>();
            }
            items.add(key);
            lineAyahs.put(line, items);

            final RectF boundsRect = b.getBounds();
            if (boundsRect.contains(x, y)) {
               return getAyahFromKey(key);
            }

            int delta = Math.min((int) Math.abs(boundsRect.bottom - y),
                    (int) Math.abs(boundsRect.top - y));
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

                  final RectF boundsRect = b.getBounds();
                  if (b.getLine() == closestLine){
                     // if x is within the x of this ayah, that's our answer
                     if (boundsRect.right >= x && boundsRect.left <= x){
                        return getAyahFromKey(ayah);
                     }

                     // otherwise, keep track of the least delta and return it
                     int delta = Math.min((int) Math.abs(boundsRect.right - x),
                             (int) Math.abs(boundsRect.left - x));
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

  public static AyahToolBar.AyahToolBarPosition getToolBarPosition(
      @NonNull List<AyahBounds> bounds, @NonNull Matrix matrix,
      int screenWidth, int screenHeight, int toolBarWidth, int toolBarHeight) {
    boolean isToolBarUnderAyah = false;
    AyahToolBar.AyahToolBarPosition result = null;
    final int size = bounds.size();

    RectF chosenRect;
    if (size > 0) {
      RectF firstRect = new RectF();
      AyahBounds chosen = bounds.get(0);
      matrix.mapRect(firstRect, chosen.getBounds());
      chosenRect = new RectF(firstRect);

      float y = firstRect.top - toolBarHeight;
      if (y < toolBarHeight) {
        // too close to the top, let's move to the bottom
        chosen = bounds.get(size - 1);
        matrix.mapRect(chosenRect, chosen.getBounds());
        y = chosenRect.bottom;
        if (y > (screenHeight - toolBarHeight)) {
          y = firstRect.bottom;
          chosenRect = firstRect;
        }
        isToolBarUnderAyah = true;
      }

      final float midpoint = chosenRect.centerX();
      float x = midpoint - (toolBarWidth / 2);
      if (x < 0 || x + toolBarWidth > screenWidth) {
        x = chosenRect.left;
        if (x + toolBarWidth > screenWidth) {
          x = screenWidth - toolBarWidth;
        }
      }

      result = new AyahToolBar.AyahToolBarPosition();
      result.x = x;
      result.y = y;
      result.pipOffset = midpoint - x;
      result.pipPosition = isToolBarUnderAyah ?
          AyahToolBar.PipPosition.UP : AyahToolBar.PipPosition.DOWN;
    }
    return result;
  }

  private static float[] getPageXY(
      float screenX, float screenY, ImageView imageView) {
    if (imageView.getDrawable() == null) {
      return null;
    }

    float[] results = null;
    Matrix inverse = new Matrix();
    if (imageView.getImageMatrix().invert(inverse)) {
      results = new float[2];
      inverse.mapPoints(results, new float[]{screenX, screenY});
    }
    return results;
  }

  public static RectF getYBoundsForHighlight(
      Map<String, List<AyahBounds>> coordinateData, int sura, int ayah) {
    if (coordinateData == null ||
        coordinateData.get(sura + ":" + ayah) == null) {
      return null;
    }


    RectF ayahBoundsRect = null;
    final List<AyahBounds> ayahBounds = coordinateData.get(sura + ":" + ayah);
    for (AyahBounds bounds : ayahBounds) {
      if (ayahBoundsRect == null) {
        ayahBoundsRect = bounds.getBounds();
      } else {
        ayahBoundsRect.union(bounds.getBounds());
      }
    }

    return ayahBoundsRect;
  }
}
