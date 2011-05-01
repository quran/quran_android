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
import android.widget.TextView;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.common.QuranGalleryAdapter;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.GalleryFriendlyScrollView;

public class ExpViewActivity extends GestureQuranActivity {
	
//	private void adjustLockView() {
//		if (QuranSettings.getInstance().isLockOrientation()) {
//			btnLockOrientation.setImageResource(R.drawable.lock);		
//		} else {
//			btnLockOrientation.setImageResource(R.drawable.unlock);
//		}
//	}
	
	public class QuranGalleryImageAdapter extends QuranGalleryAdapter {
		private Map<String, SoftReference<Bitmap>> cache = 
            new HashMap<String, SoftReference<Bitmap>>();
		
	    public QuranGalleryImageAdapter(Context context) {
	    	super(context);
	    }
	    
	    @Override
	    public void emptyCache() {
	    	super.emptyCache();
	    	cache.clear();
	    }

	    public View getView(int position, View convertView, ViewGroup parent) {
	    	PageHolder holder;
	    	if (convertView == null){
	    		convertView = mInflater.inflate(R.layout.quran_page, null);
				holder = new PageHolder();
				holder.page = (ImageView)convertView.findViewById(R.id.pageImageView);
				holder.scroll = (GalleryFriendlyScrollView)convertView.findViewById(R.id.pageScrollView);
				holder.txtPageNotFound = (TextView)convertView.findViewById(R.id.txtPageNotFound);
				convertView.setTag(holder);
	    	}
	    	else {
	    		holder = (PageHolder)convertView.getTag();
	    	}
	    	
	        int page = ApplicationConstants.PAGES_LAST - position;
	        Bitmap bitmap = getBitmap(page);
	        
	        if (bitmap == null) {
	        	Log.d("QuranAndroid", "Page not found: " + page);
	        	adjustView(holder, true);
	        } else {
	        	holder.page.setImageBitmap(bitmap);
	        	adjustView(holder, false);
				QuranSettings.getInstance().setLastPage(page);
				QuranSettings.save(prefs);
	        }
	        
			if (!inReadingMode)
				updatePageInfo(position);
			adjustBookmarkView();
	    	return convertView;
	    }
	    
	    private void adjustView(PageHolder holder, boolean pageNotFound) {
	    	if (pageNotFound) {
	    		holder.txtPageNotFound.setVisibility(View.VISIBLE);
	        	holder.page.setVisibility(View.GONE);
	    	} else {
	    		holder.txtPageNotFound.setVisibility(View.GONE);
	        	holder.page.setVisibility(View.VISIBLE);
	    	}
	    }
	    
	    private Bitmap getBitmap(int page) {
	    	Bitmap bitmap = null;
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
	        
	        return bitmap;
	    }
	}
	
	static class PageHolder {
		TextView txtPageNotFound;
		ImageView page;
		ScrollView scroll;
	}

	@Override
	protected void initGalleryAdapter() {
		galleryAdapter = new QuranGalleryImageAdapter(this);
	}
	
}
