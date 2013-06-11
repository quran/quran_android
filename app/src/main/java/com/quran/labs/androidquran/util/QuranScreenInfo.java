package com.quran.labs.androidquran.util;

import android.app.Activity;
import android.content.Context;
import android.view.Display;
import android.view.WindowManager;
import com.quran.labs.androidquran.R;

public class QuranScreenInfo {

	private static QuranScreenInfo sInstance = null;
	
	private int mHeight;
   private int mMinWidth;
	private int mMaxWidth;

	private QuranScreenInfo(int width, int height){
		mHeight = height;
      mMinWidth = (width > height)? height : width;
		mMaxWidth = (width > height)? width : height;
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
      return new QuranScreenInfo(d.getWidth(), d.getHeight());
   }

	public static void initialize(int width, int height){
		sInstance = new QuranScreenInfo(width, height);
	}

	public int getHeight(){ return mHeight; }

	public String getWidthParam(){
		return "_" + getWidthParamNoUnderScore();
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
      else return "1920";
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
