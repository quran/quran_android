package com.quran.labs.androidquran;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.common.QuranGalleryAdapter;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.GalleryFriendlyScrollView;

public class ExpViewActivity extends GestureQuranActivity {
	
	private DownloadBitmapTask currentTask;
	private ProgressDialog progressDialog;
	
//	private void adjustLockView() {
//		if (QuranSettings.getInstance().isLockOrientation()) {
//			btnLockOrientation.setImageResource(R.drawable.lock);		
//		} else {
//			btnLockOrientation.setImageResource(R.drawable.unlock);
//		}
//	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		progressDialog = new ProgressDialog(this);
		super.onCreate(savedInstanceState);
	}
	
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
	        	if (currentTask == null || currentTask.getStatus() != Status.RUNNING) {
	        		currentTask = new DownloadBitmapTask(position, page);
	        		connect();
	        	}
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
	        
	        // Bitmap not found in cache..
	    	if (bitmap == null){
	        	String filename = getPageFileName(page);
	        	bitmap = QuranUtils.getImageFromSD(filename);
	        	// Add Bitmap to cache..
	        	if (bitmap != null)
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
	
	@Override
	protected void onConnectionSuccess() {
		super.onConnectionSuccess();
		if (currentTask != null)
			currentTask.execute(null);
	}
	
	private class DownloadBitmapTask extends AsyncTask<Object[], Object, Object> {
		private int position, page;
		private boolean downloaded = false; 

		public DownloadBitmapTask(int position, int page) {
			this.position = position;
			this.page = page;
		}

		protected void onPreExecute() {
			progressDialog.setMessage("Downloading page (" + page + ").. Please wait..");
			progressDialog.show();
		}

		@Override
		public void onPostExecute(Object result) {
			if (downloaded) {
				galleryAdapter.notifyDataSetChanged();
			} else {
				Toast.makeText(getApplicationContext(), "Error downloading page..", Toast.LENGTH_SHORT);
			}
			currentTask = null;
			progressDialog.hide();
		}

		@Override
		protected Object doInBackground(Object[]... arg0) {
			Bitmap b = QuranUtils.getImageFromWeb(getPageFileName(page));
			downloaded = b != null;
			return null;
		}
	}
	
}
