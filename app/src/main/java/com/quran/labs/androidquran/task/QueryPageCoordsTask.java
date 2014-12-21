package com.quran.labs.androidquran.task;

import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;

import android.content.Context;
import android.graphics.RectF;
import android.os.AsyncTask;

/**
 * Created with IntelliJ IDEA.
 * User: ahmedre
 * Date: 5/9/13
 * Time: 10:05 PM
 */
public class QueryPageCoordsTask extends AsyncTask<Integer, Void, RectF[]> {
   private AyahInfoDatabaseHandler mAyahInfoDatabaseHandler;

   public QueryPageCoordsTask(Context context, String widthParam){
      mAyahInfoDatabaseHandler = null;
      if (context != null && context instanceof PagerActivity){
         mAyahInfoDatabaseHandler =
                 ((PagerActivity)context).getAyahInfoDatabase(widthParam);
      }
   }

   @Override
   protected RectF[] doInBackground(Integer... params){
      if (params == null || mAyahInfoDatabaseHandler == null){ return null; }
      RectF[] result = new RectF[params.length];
      for (int i=0; i<params.length; i++){
         result[i] = mAyahInfoDatabaseHandler.getPageBounds(params[i]);
      }
      return result;
   }
}
