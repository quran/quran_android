package com.quran.labs.androidquran.util;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Bitmap;

public class BitmapUtils {

	private static Map<String, SoftReference<Bitmap>> cache = 
	        new HashMap<String, SoftReference<Bitmap>>();
	
	public static Bitmap getBitmap(int page){
		Bitmap bitmap = null;
    	if (cache != null && cache.containsKey("page_" + page)){
        	SoftReference<Bitmap> bitmapRef = cache.get("page_" + page);
        	bitmap = bitmapRef.get();
        }
    	
        // Bitmap not found in cache..
    	if (bitmap == null){
        	String filename = QuranFileUtils.getPageFileName(page);
        	bitmap = QuranFileUtils.getImageFromSD(filename);
        	// Add Bitmap to cache..
        	if (bitmap != null) {
        		cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
        	} else {
        		bitmap = QuranFileUtils.getImageFromWeb(filename);
        		if (bitmap != null)
            		cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
        	}
        }
        
        return bitmap;
	}
}
