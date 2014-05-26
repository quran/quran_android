package com.quran.labs.androidquran.task;

import android.content.Context;
import android.graphics.Rect;
import android.os.AsyncTask;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;

/**
 * Created with IntelliJ IDEA.
 * User: ahmedre
 * Date: 5/9/13
 * Time: 10:05 PM
 */
public class QueryPageCoordsTask extends AsyncTask<Integer, Void, Rect[]> {
   private AyahInfoDatabaseHandler mAyahInfoDatabaseHandler;

   public QueryPageCoordsTask(Context context, String widthParam){
      mAyahInfoDatabaseHandler = null;
      if (context != null && context instanceof PagerActivity){
         mAyahInfoDatabaseHandler =
                 ((PagerActivity)context).getAyahInfoDatabase(widthParam);
      }
   }

   @Override
   protected Rect[] doInBackground(Integer... params){
      if (params == null || mAyahInfoDatabaseHandler == null){ return null; }
      Rect[] result = new Rect[params.length];
      for (int i=0; i<params.length; i++){
         result[i] = mAyahInfoDatabaseHandler.getPageBounds(params[i]);
      }
      return result;
   }
}
