package com.quran.labs.androidquran.common;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.quran.labs.androidquran.ExpViewActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.QuranPageCurlView;
import com.quran.labs.androidquran.widgets.QuranPageCurlView.OnPageFlipListener;


/**
 * 
 * @author wnafee
 * 
 * This class takes care of loading pages into the mushaf when needed.
 * 
 * If the image is in the SD or cache then it retrieves it, otherwise, it downloads 
 * the page from the server. As the pages are being flipped, it keeps the page stack
 * filled to allow for proper animation from the user's perspective
 *
 */
public class QuranPageFeeder implements OnPageFlipListener {
	
	private static final String TAG = "QuranPageFeeder";
	
	private Map<String, SoftReference<Bitmap>> cache = 
        new HashMap<String, SoftReference<Bitmap>>();
	
	private ExpViewActivity mContext;
	private QuranPageCurlView mQuranPage;
	
	private LayoutInflater mInflater;
	private int mPageLayout;
	private int mCurrentPageNumber;
	
	private ProgressDialog progressDialog;
	private DownloadBitmapTask currentTask;

	public QuranPageFeeder(ExpViewActivity context, QuranPageCurlView quranPage, int page_layout) {
		mContext = context;
		mInflater = LayoutInflater.from(context);
		mPageLayout = page_layout;
		mQuranPage = quranPage;
		mQuranPage.setOnPageFlipListener(this);
		mCurrentPageNumber = 0;
	}
	
	public int getCurrentPagePosition() {
		return mCurrentPageNumber;
	}
	
	public void jumpToPage(int page){
		mCurrentPageNumber = page;
		
		if (page == ApplicationConstants.PAGES_FIRST) {
			mQuranPage.addNextPage(null);
			mQuranPage.addNextPage(createPage(page));
			mQuranPage.addNextPage(createPage(page+1));
		} else if (page == ApplicationConstants.PAGES_LAST){
			mQuranPage.addPreviousPage(null);
			mQuranPage.addPreviousPage(createPage(page));
			mQuranPage.addPreviousPage(createPage(page-1));
		} else {
			mQuranPage.addNextPage(createPage(page-1));
			mQuranPage.addNextPage(createPage(page));
			mQuranPage.addNextPage(createPage(page+1));
		}
		
		// TODO: how do i handle multiple downloads at the same time??
		
		// TODO: do i need to invalidate to redraw?
		mQuranPage.refresh(true);
	}
	
	public void refreshCurrent() {
		jumpToPage(mCurrentPageNumber);
	}

	@Override
	public void onPageFlipBegin(QuranPageCurlView pageView, int flipDirection) {
		// Does nothing
	}

	@Override
	public void onPageFlipEnd(QuranPageCurlView pageView, int flipDirection) {
		if (flipDirection == OnPageFlipListener.NEXT_PAGE){
			loadNextPage(pageView);
		} else if (flipDirection == OnPageFlipListener.PREVIOUS_PAGE){
			loadPreviousPage(pageView);
		}
	}
	
	public int loadNextPage(QuranPageCurlView pageView) {
		mCurrentPageNumber += 1;
		if (mCurrentPageNumber+1 < ApplicationConstants.PAGES_LAST){
			Log.d(TAG, "Adding Next Page: " + (mCurrentPageNumber+1));
			View v = createPage(mCurrentPageNumber+1);
			pageView.addNextPage(v);
		} else {
			pageView.addNextPage((View)null); // add empty page to prevent coming here again
		}
		return mCurrentPageNumber;
	}
	
	public int loadPreviousPage(QuranPageCurlView pageView){
		mCurrentPageNumber -= 1;
		if (mCurrentPageNumber > ApplicationConstants.PAGES_FIRST ) {
			Log.d(TAG, "Adding Previous Page: " + (mCurrentPageNumber-1));
			View v = createPage(mCurrentPageNumber-1);
			pageView.addPreviousPage(v);
		} else {
			pageView.addPreviousPage((View)null); // add empty page to prevent coming here again
		}
		return mCurrentPageNumber;
	}
	
	private View createPage(int index) {
		View v = mInflater.inflate(mPageLayout, null);
		ImageView iv = (ImageView)v.findViewById(R.id.page_image);
		
		ScrollView sv = (ScrollView)v.findViewById(R.id.page_scroller);
		if (sv == null)
			v.setTag(new Boolean(false));
		else
			v.setTag(new Boolean(true));
		
		Bitmap bitmap = getBitmap(index);
		if (bitmap == null) {
        	Log.d(TAG, "Page not found: " + index);
        	updateViewForUser(v, true);
        	if (currentTask == null || currentTask.getStatus() != Status.RUNNING) {
        		currentTask = new DownloadBitmapTask(iv, index);
        		connect();
        	}
        } else {
        	iv.setImageBitmap(bitmap);
        	updateViewForUser(v, false);
        	QuranSettings.getInstance().setLastPage(index);
			QuranSettings.save(mContext.prefs);
        }
		
		return v;
	}
	
	private void updateViewForUser(View v, boolean pageNotFound){
		TextView tv = (TextView)v.findViewById(R.id.txtPageNotFound);
		ImageView iv = (ImageView)v.findViewById(R.id.page_image);
		if (pageNotFound) {
    		tv.setVisibility(View.VISIBLE);
        	iv.setVisibility(View.GONE);
    	} else {
    		tv.setVisibility(View.GONE);
        	iv.setVisibility(View.VISIBLE);
    	}
	}
	
	private Bitmap getBitmap(int page) {
    	Bitmap bitmap = null;
    	if (cache != null && cache.containsKey("page_" + page)){
        	SoftReference<Bitmap> bitmapRef = cache.get("page_" + page);
        	bitmap = bitmapRef.get();
        	Log.d(TAG, "reading image for page " + page + " from cache!");
        } else {
        	Log.d(TAG, "loading image for page " + page + " from sdcard");
        }
        
        // Bitmap not found in cache..
    	if (bitmap == null){
        	String filename = mContext.getPageFileName(page);
        	bitmap = QuranUtils.getImageFromSD(filename);
        	// Add Bitmap to cache..
        	if (bitmap != null) {
        		cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
        		Log.d(TAG, "page " + page + " added to cache!");
        	} else {
        		Log.d(TAG, "page " + page + " not found on sdcard");
        	}
        }
        
        return bitmap;
    }
	
	protected void connect() {
		if (mContext.isInternetOn())
        	onConnectionSuccess();
        else
        	mContext.onConnectionFailed();
	}
	
	protected void onConnectionSuccess() {
		if (currentTask != null)
			currentTask.execute();
	}
	
	private class DownloadBitmapTask extends AsyncTask<Void, Void, Bitmap> {
		
		private ImageView image;
		private int page;
		private boolean downloaded = false; 

		public DownloadBitmapTask(ImageView iv, int page) {
			this.page = page;
			this.image = iv; 
		}

		protected void onPreExecute() {
			if (progressDialog != null) {
				progressDialog.setMessage("Downloading page (" + page + ").. Please wait..");
				progressDialog.show();
			}
		}

		@Override
		public void onPostExecute(Bitmap bitmap) {
			if (downloaded) {
				image.setImageBitmap(bitmap);
				QuranPageFeeder.this.mQuranPage.refresh();
				
				// add bitmap to cache
				cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
        		Log.d(TAG, "page " + page + " added to cache!");
        		
			} else {
				Toast.makeText(mContext, "Error downloading page..", Toast.LENGTH_SHORT);
			}
			
			//Release memory for GC
			image = null;
			currentTask = null;
			
			if (progressDialog != null)
				progressDialog.hide();
		}

		@Override
		protected Bitmap doInBackground(Void... arg0) {
			Bitmap b = QuranUtils.getImageFromWeb(QuranPageFeeder.this.mContext.getPageFileName(page));
			downloaded = b != null;
			return b;
		}
	}

}
