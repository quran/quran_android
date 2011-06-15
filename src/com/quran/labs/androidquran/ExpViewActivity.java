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
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.GalleryFriendlyScrollView;

public class ExpViewActivity extends GestureQuranActivity {
	
	private static final String TAG = "ExpViewActivity";
	
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
		Object [] saved = (Object []) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			currentTask = (DownloadBitmapTask) saved[0];
			galleryAdapter = (QuranGalleryImageAdapter) saved[1];
			quranPageFeeder = (QuranPageFeeder) saved[2];
		} 
		super.onCreate(savedInstanceState);
		progressDialog = new ProgressDialog(this);
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		Object [] o = {currentTask, galleryAdapter, quranPageFeeder};
		return o;
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		// Always initialize Quran Screen on start so as to be able to retrieve images
		// Error cause: Gallery Adapter was unable to retrieve images from SDCard as QuranScreenInfo
		// was cleared after long sleep..
		initializeQuranScreen();
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
	    	
	    	Log.d("exp_v", "position: " + position);
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
	    	if (cache != null && cache.containsKey("page_" + page)){
	        	SoftReference<Bitmap> bitmapRef = cache.get("page_" + page);
	        	bitmap = bitmapRef.get();
	        	Log.d("exp_v", "reading image for page " + page + " from cache!");
	        } else {
	        	Log.d("exp_v", "loading image for page " + page + " from sdcard");
	        }
	        
	        // Bitmap not found in cache..
	    	if (bitmap == null){
	        	String filename = getPageFileName(page);
	        	bitmap = QuranUtils.getImageFromSD(filename);
	        	// Add Bitmap to cache..
	        	if (bitmap != null) {
	        		cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
	        		Log.d("exp_v", "page " + page + " added to cache!");
	        	} else {
	        		Log.d("exp_v", "page " + page + " not found on sdcard");
	        	}
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
		if (galleryAdapter == null) {
			Log.d("exp_v", "Adapter instantiated..");
			galleryAdapter = new QuranGalleryImageAdapter(this);
		}
	}
	
	protected void initQuranPageFeeder(){
		if (quranPageFeeder == null) {
			Log.d(TAG, "Quran Feeder instantiated...");
			quranPageFeeder = new QuranPageFeeder(this, quranPageCurler, R.layout.quran_page_layout);
		}
	}
	
	@Override
	protected void onConnectionSuccess() {
		super.onConnectionSuccess();
		if (currentTask != null)
			currentTask.execute();
	}
	
	private class DownloadBitmapTask extends AsyncTask<Void, Object, Object> {
		private int page;
		private boolean downloaded = false; 

		public DownloadBitmapTask(int position, int page) {
			this.page = page;
		}

		protected void onPreExecute() {
			if (progressDialog != null) {
				progressDialog.setMessage("Downloading page (" + page + ").. Please wait..");
				progressDialog.show();
			}
		}

		@Override
		public void onPostExecute(Object result) {
			if (downloaded) {
				if (galleryAdapter != null)
					galleryAdapter.notifyDataSetChanged();
			} else {
				Toast.makeText(getApplicationContext(), "Error downloading page..", Toast.LENGTH_SHORT);
			}
			currentTask = null;
			if (progressDialog != null)
				progressDialog.hide();
		}

		@Override
		protected Object doInBackground(Void... arg0) {
			Bitmap b = QuranUtils.getImageFromWeb(getPageFileName(page));
			downloaded = b != null;
			return null;
		}
	}
	
}
