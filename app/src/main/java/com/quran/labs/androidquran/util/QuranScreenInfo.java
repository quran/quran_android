package com.quran.labs.androidquran.util;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;

public class QuranScreenInfo {
  private static final String TAG =
      "com.quran.labs.androidquran.util.QuranScreenInfo";
	private static QuranScreenInfo sInstance = null;
	
	private int mHeight;
	private int mMaxWidth;
  private int mMaxBitmapHeight;

	private QuranScreenInfo(int width, int height){
		mHeight = height;
		mMaxWidth = (width > height)? width : height;
    Log.d(TAG, "initializing with " + height + " and " + width);
  }
	
	public static QuranScreenInfo getInstance(){
		return sInstance;
	}

   public static QuranScreenInfo getOrMakeInstance(Activity activity){
      if (sInstance == null){
         sInstance = initialize(activity);
      }
      return sInstance;
   }

   private static QuranScreenInfo initialize(Activity activity){
      WindowManager w = activity.getWindowManager();
      Display d = w.getDefaultDisplay();
      QuranScreenInfo qsi = new QuranScreenInfo(d.getWidth(), d.getHeight());
      if ("1920".equals(qsi.getWidthParamNoUnderScore())){
        SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(activity.getApplicationContext());
        int height = prefs.getInt(Constants.PREF_MAX_BITMAP_HEIGHT, -1);
        if (height > -1){
          qsi.setBitmapMaxHeight(height);
          Log.d(TAG, "max height in prefs is set to " + height);
        }
      }
      return qsi;
   }

	public int getHeight(){ return mHeight; }

	public String getWidthParam(){
		return "_" + getWidthParamNoUnderScore();
	}

  public void setBitmapMaxHeight(int height){
    mMaxBitmapHeight = height;
  }

  public int getBitmapMaxHeight(){
    return mMaxBitmapHeight;
  }

  public String getTabletWidthParam(){
      int width = mMaxWidth / 2;
      return "_" + getBestTabletLandscapeSizeMatch(width);
   }

   public String getWidthParamNoUnderScore(){
      // the default image size is based on the width
      return getWidthParamNoUnderScore(mMaxWidth);
   }

   private String getWidthParamNoUnderScore(int width){
		if (width <= 320){ return "320"; }
		else if (width <= 480){ return "480"; }
		else if (width <= 800){ return "800"; }
		else if (width <= 1280){ return "1024"; }
    else {
      if (mMaxBitmapHeight == -1 || mMaxBitmapHeight >= 3106){
        return "1920";
      }
      else { return "1024"; }
    }
	}

   private String getBestTabletLandscapeSizeMatch(int width){
      if (width <= 640){ return "512"; }
      else { return "1024"; }
   }

   public boolean isTablet(Context context){
      if (context != null && mMaxWidth > 800){
         return context.getResources()
                 .getBoolean(R.bool.is_tablet);
      }
      return false;
   }
}
