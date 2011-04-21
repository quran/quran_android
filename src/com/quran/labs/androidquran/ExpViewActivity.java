package com.quran.labs.androidquran;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.common.QuranGalleryAdapter;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.GalleryFriendlyScrollView;

public class ExpViewActivity extends GestureQuranActivity {
	
	private void adjustLockView() {
//		if (QuranSettings.getInstance().isLockOrientation()) {
//			btnLockOrientation.setImageResource(R.drawable.lock);		
//		} else {
//			btnLockOrientation.setImageResource(R.drawable.unlock);
//		}
	}
	
	public class QuranGalleryImageAdapter extends QuranGalleryAdapter {
		private Map<String, SoftReference<Bitmap>> cache = 
            new HashMap<String, SoftReference<Bitmap>>();
		
	    public QuranGalleryImageAdapter(Context context) {
	    	super(context);
	    }

	    public View getView(int position, View convertView, ViewGroup parent) {
	    	PageHolder holder;
	    	if (convertView == null){
	    		convertView = mInflater.inflate(R.layout.quran_page, null);
				holder = new PageHolder();
				holder.page = (ImageView)convertView.findViewById(R.id.pageImageView);
				holder.scroll = (GalleryFriendlyScrollView)convertView.findViewById(R.id.pageScrollView);
				convertView.setTag(holder);
	    	}
	    	else {
	    		holder = (PageHolder)convertView.getTag();
	    	}
	    	
	        Bitmap bitmap = null;
	        int page = ApplicationConstants.PAGES_LAST - position;
	        if (cache.containsKey("page_" + page)){
	        	SoftReference<Bitmap> bitmapRef = cache.get("page_" + page);
	        	bitmap = bitmapRef.get();
	        	Log.d("exp_v", "reading image for page " + page + " from cache!");
	        }
	        
	        if (bitmap == null){
	        	String filename = getPageFileName(page);
	        	bitmap = QuranUtils.getImageFromSD(filename);
	        	cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
	        }
			holder.page.setImageBitmap(bitmap);
			QuranSettings.getInstance().setLastPage(page);
			QuranSettings.save(prefs);
			
			if (!inReadingMode)
				updatePageInfo(position);
			adjustBookmarkView();
	    	return convertView;
	    }
	}
	
	static class PageHolder {
		ImageView page;
		ScrollView scroll;
	}

	@Override
	protected QuranGalleryAdapter getAdapter() {
		return new QuranGalleryImageAdapter(this);
	}
	
}
