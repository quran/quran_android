package com.quran.labs.androidquran.ui.fragment;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.util.LruCache;
import android.util.Log;

public class ImageCacheFragment extends Fragment {
   private static final String TAG = "ImageCacheFragment";
   public LruCache<Integer, Bitmap> mRetainedCache;

   public static ImageCacheFragment getImageCacheFragment(FragmentManager fm){
      ImageCacheFragment fragment =
              (ImageCacheFragment)fm.findFragmentByTag(TAG);
      if (fragment == null){
         Log.d(TAG, "making fragment...");
         fragment = new ImageCacheFragment();
         fm.beginTransaction().add(fragment, TAG).commit();
      }
      return fragment;
   }

   public ImageCacheFragment(){}

   @Override
   public void onCreate(Bundle savedInstanceState){
      super.onCreate(savedInstanceState);
      setRetainInstance(true);
   }
}
