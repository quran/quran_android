package com.quran.labs.androidquran.ui.util;

import android.util.Log;
import android.util.SparseArray;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.widgets.HighlightingImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: ahmedre
 * Date: 5/11/13
 * Time: 10:34 PM
 */
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

      float[] pageXY = imageView.getPageXY(xc, yc);
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
}
