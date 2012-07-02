package com.quran.labs.androidquran.util;

import android.content.Context;
import com.quran.labs.androidquran.R;

public class AudioUtils {
   public static String[] mQariBaseUrls = null;

   public static String getQariUrl(Context context, int position){
      if (mQariBaseUrls == null){
         mQariBaseUrls = context.getResources()
                 .getStringArray(R.array.quran_readers_urls);
      }

      if (position >= mQariBaseUrls.length || 0 > position){ return null; }
      return mQariBaseUrls[position];
   }
}
