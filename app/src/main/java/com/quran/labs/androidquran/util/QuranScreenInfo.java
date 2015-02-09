package com.quran.labs.androidquran.util;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

public class QuranScreenInfo {
  private static final String TAG = "QuranScreenInfo";
	private static QuranScreenInfo sInstance = null;
	
	private int mHeight;
	private int mMaxWidth;
  private String mOverrideParam;

	private QuranScreenInfo(int width, int height){
		mHeight = height;
		mMaxWidth = (width > height)? width : height;
    Log.d(TAG, "initializing with " + height + " and " + width);
  }
	
	public static QuranScreenInfo getInstance(){
		return sInstance;
	}

   public static QuranScreenInfo getOrMakeInstance(Context context){
      if (sInstance == null){
         sInstance = initialize(context);
      }
      return sInstance;
   }

   private static QuranScreenInfo initialize(Context context){
      WindowManager w = (WindowManager)context
          .getSystemService(Context.WINDOW_SERVICE);
      Display d = w.getDefaultDisplay();
      QuranScreenInfo qsi = new QuranScreenInfo(d.getWidth(), d.getHeight());
      final SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(context.getApplicationContext());
      qsi.setOverrideParam(prefs.getString(Constants.PREF_DEFAULT_IMAGES_DIR, ""));
      return qsi;
   }

  public void setOverrideParam(String overrideParam) {
    mOverrideParam = overrideParam;
  }

	public int getHeight(){ return mHeight; }

	public String getWidthParam(){
		return "_" + getWidthParamNoUnderScore();
	}

  public String getTabletWidthParam(){
      if ("_1260".equals(getWidthParam())) {
        // for tablet, if the width is more than 1280, use 1260
        // images for both dimens (only applies to new installs)
        return "_1260";
      } else {
        int width = mMaxWidth / 2;
        return "_" + getBestTabletLandscapeSizeMatch(width);
      }
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
      if (!TextUtils.isEmpty(mOverrideParam)) {
        return mOverrideParam;
      }
      return "1260";
    }
	}

   private String getBestTabletLandscapeSizeMatch(int width){
      if (width <= 640){ return "512"; }
      else { return "1024"; }
   }

   public boolean isTablet(Context context){
     return context != null && mMaxWidth > 800 && context.getResources()
         .getBoolean(R.bool.is_tablet);
   }
}
