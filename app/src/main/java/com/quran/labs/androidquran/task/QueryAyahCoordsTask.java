package com.quran.labs.androidquran.task;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.view.MotionEvent;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.HighlightType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: ahmedre
 * Date: 5/9/13
 * Time: 10:28 PM
 */
public class QueryAyahCoordsTask extends
        AsyncTask<Integer, Void, List<Map<String, List<AyahBounds>>>> {
   protected int mSura;
   protected int mAyah;
   protected int mPage;
   protected String mWidthParam;
   protected boolean mHighlightAyah;
   protected HighlightType mHighlightType;
   protected MotionEvent mEvent;
   protected AyahSelectedListener.EventType mEventType;
   private AyahInfoDatabaseHandler mAyahInfoDatabaseHandler;

   public QueryAyahCoordsTask(Context context, String widthParam){
      this(context, widthParam, 0, 0, null);
      mHighlightAyah = false;
   }

   public QueryAyahCoordsTask(Context context, MotionEvent event, AyahSelectedListener.EventType eventType,
                              String widthParam, int page){
      this(context, widthParam, 0, 0, null);
      mEvent = event;
      mEventType = eventType;
      mHighlightAyah = false;
      mPage = page;
   }

   public QueryAyahCoordsTask(Context context, String widthParam,
                              int sura, int ayah, HighlightType type){
      mSura = sura;
      mAyah = ayah;
      mHighlightAyah = true;
      mHighlightType = type;
      mWidthParam = widthParam;
      mAyahInfoDatabaseHandler = null;

      if (context != null && context instanceof PagerActivity){
         mAyahInfoDatabaseHandler =
                 ((PagerActivity)context).getAyahInfoDatabase(mWidthParam);
      }
   }

   @Override
   protected List<Map<String, List<AyahBounds>>> doInBackground(
           Integer... params){
      if (mAyahInfoDatabaseHandler == null || params == null){ return null; }

      List<Map<String, List<AyahBounds>>> result =
              new ArrayList<Map<String, List<AyahBounds>>>();

      for (int i=0; i < params.length; i++){
         Cursor cursor = null;
         try {
            cursor = mAyahInfoDatabaseHandler
                    .getVersesBoundsForPage(params[i]);

            if (cursor == null || !cursor.moveToFirst()){ return null; }

            Map<String, List<AyahBounds>> map =
                    new HashMap<String, List<AyahBounds>>();
            do {
               int sura = cursor.getInt(2);
               int ayah = cursor.getInt(3);
               String key = sura + ":" + ayah;
               List<AyahBounds> bounds = map.get(key);
               if (bounds == null){
                  bounds = new ArrayList<AyahBounds>();
               }

               AyahBounds last = null;
               if (bounds.size() > 0){ last = bounds.get(bounds.size() - 1); }

               AyahBounds bound = new AyahBounds(cursor.getInt(1),
                       cursor.getInt(4), cursor.getInt(5),
                       cursor.getInt(6), cursor.getInt(7),
                       cursor.getInt(8));
               if (last != null && last.getLine() == bound.getLine()){
                  last.engulf(bound);
               }
               else { bounds.add(bound); }
               map.put(key, bounds);
            }
            while (cursor.moveToNext());

            result.add(map);
         }
         catch (Exception e){
            // happens when the glyphs table doesn't exist somehow
            return null;
         }
         finally {
            if (cursor != null){
               try { cursor.close(); } catch (Exception e){ }
            }
         }
      }

      return result;
   }
}
