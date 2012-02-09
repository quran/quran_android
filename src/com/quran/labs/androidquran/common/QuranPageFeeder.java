package com.quran.labs.androidquran.common;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
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
	
	protected PageViewQuranActivity mContext;
	protected QuranPageCurlView mQuranPage;
	
	protected LayoutInflater mInflater;
	protected int mPageLayout;
	protected int mCurrentPageNumber;
	private Boolean mHasBeenOutOfMemory;
	
	private long lastPopupTime = System.currentTimeMillis();
	
	public QuranPageFeeder(PageViewQuranActivity context,
			QuranPageCurlView quranPage, int page_layout) {
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
		if (page <= ApplicationConstants.PAGES_FIRST) {
			page = ApplicationConstants.PAGES_FIRST;
			mQuranPage.addNextPage(null);
			mQuranPage.addNextPage(createPage(page));
			mQuranPage.addNextPage(createPage(page+1));
		} else if (page >= ApplicationConstants.PAGES_LAST){
			page = ApplicationConstants.PAGES_LAST;
			mQuranPage.addPreviousPage(null);
			mQuranPage.addPreviousPage(createPage(page));
			mQuranPage.addPreviousPage(createPage(page-1));
		} else {
			mQuranPage.addNextPage(createPage(page-1));
			mQuranPage.addNextPage(createPage(page));
			mQuranPage.addNextPage(createPage(page+1));
		}
		mCurrentPageNumber = page;
		QuranSettings.getInstance().setLastPage(page);
		QuranSettings.save(mContext.prefs);
		mQuranPage.refresh(true); 
	}
	
	public void goToNextpage() {
		mQuranPage.doPageFlip(OnPageFlipListener.NEXT_PAGE);
		/* Keeping old code in case new solution is too buggy
		if (mCurrentPageNumber+1 > ApplicationConstants.PAGES_LAST)
			return;
		loadNextPage(mQuranPage);
		mQuranPage.refresh(true);
		*/
	}
	
	public void goToPreviousPage() {
		mQuranPage.doPageFlip(OnPageFlipListener.PREVIOUS_PAGE);
		/* Keeping old code in case new solution is too buggy
		if (mCurrentPageNumber-1 < ApplicationConstants.PAGES_FIRST)
			return;
		loadPreviousPage(mQuranPage);
		mQuranPage.refresh(true);
		*/
	}
	
	public void highlightAyah(int sura, int ayah){
		View v = mQuranPage.getCurrentPage();
		HighlightingImageView iv = 
			(HighlightingImageView)v.findViewById(R.id.page_image);
		if (iv != null){
			HighlightingImageView hi = (HighlightingImageView)iv;
			hi.highlightAyah(sura, ayah);
			if (QuranSettings.getInstance().isAutoScroll() && v.getResources()
					.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
				AyahBounds yBounds = hi.getYBoundsForCurrentHighlight();
				if (yBounds != null)
					mQuranPage.scrollToAyah(R.id.page_scroller, yBounds);
			}
			mQuranPage.invalidate();
		}
	}
	
	public void unHighlightAyah(){
		View v = mQuranPage.getCurrentPage();
		if (v != null) {
			HighlightingImageView iv = 
				(HighlightingImageView)v.findViewById(R.id.page_image);
			if (iv != null){
				HighlightingImageView hi = (HighlightingImageView)iv;
				hi.unhighlight();
				mQuranPage.invalidate();
			}
		}
	}
	
	public void refreshCurrent() {
		jumpToPage(mCurrentPageNumber);		
	}
	
	public void ScrollDown() {
		mQuranPage.scrollPage(R.id.page_scroller, View.FOCUS_DOWN);
	}
	
	public void ScrollUp() {
		mQuranPage.scrollPage(R.id.page_scroller, View.FOCUS_UP);
	}
	
	@Override
	public void onPageFlipBegin(QuranPageCurlView pageView, int flipDirection){
		// Does nothing
	}

	@Override
	public void onPageFlipEnd(QuranPageCurlView pageView, int flipDirection) {
		if (flipDirection == OnPageFlipListener.NEXT_PAGE){
			loadNextPage(pageView);
		} else if (flipDirection == OnPageFlipListener.PREVIOUS_PAGE){
			loadPreviousPage(pageView);
		}
		QuranSettings.getInstance().setLastPage(mCurrentPageNumber);
		QuranSettings.save(mContext.prefs);
	}
	
	public void displayMarkerPopup() {
		if(System.currentTimeMillis() - lastPopupTime < 3000)
			return;
		int rub3 = QuranInfo.getRub3FromPage(mCurrentPageNumber);
		if (rub3 == -1)
			return;
		int hizb = (rub3 / 4) + 1;
		StringBuilder sb = new StringBuilder();
		
		if (rub3 % 8 == 0) {
			sb.append(mContext.getString(R.string.quran_juz2)).append(' ').append((hizb/2) + 1);
		} else {
			int remainder = rub3 % 4;
			if (remainder == 1)
				sb.append(mContext.getString(R.string.quran_rob3)).append(' ');
			else if (remainder == 2)
				sb.append(mContext.getString(R.string.quran_nos)).append(' ');
			else if (remainder == 3)
				sb.append(mContext.getString(R.string.quran_talt_arb3)).append(' ');
			sb.append(mContext.getString(R.string.quran_hizb)).append(' ').append(hizb);
		}
		Toast.makeText(mContext, ArabicStyle.reshape(sb.toString()), Toast.LENGTH_SHORT).show();
		lastPopupTime = System.currentTimeMillis();
	}
	
	public int loadNextPage(QuranPageCurlView pageView) {
		mCurrentPageNumber += 1;
		if (mCurrentPageNumber < ApplicationConstants.PAGES_LAST){
			Log.d(TAG, "Adding Next Page: " + (mCurrentPageNumber+1));
			View v = createPage(mCurrentPageNumber+1);
			pageView.addNextPage(v);
			mContext.updatePageInfo(mCurrentPageNumber);
		} else {
			pageView.addNextPage((View)null); 
			mContext.updatePageInfo(mCurrentPageNumber);
			// add empty page to prevent coming here again
		}
		return mCurrentPageNumber;
	}
	
	public int loadPreviousPage(QuranPageCurlView pageView){
		mCurrentPageNumber -= 1;
		if (mCurrentPageNumber > ApplicationConstants.PAGES_FIRST ) {
			Log.d(TAG, "Adding Previous Page: " + (mCurrentPageNumber-1));
			View v = createPage(mCurrentPageNumber-1);
			pageView.addPreviousPage(v);
			mContext.updatePageInfo(mCurrentPageNumber);
		} else {
			pageView.addPreviousPage((View)null);
			mContext.updatePageInfo(mCurrentPageNumber);
			// add empty page to prevent coming here again
		}
		return mCurrentPageNumber;
	}
	
	protected View createPage(int index) {
		View v = mInflater.inflate(mPageLayout, null);
		
		ScrollView sv = (ScrollView)v.findViewById(R.id.page_scroller);
		if (sv == null)
			v.setTag(new Boolean(false));
		else
			v.setTag(new Boolean(true));
		
		updateViewForUser(v, true, false);
		
		// Get page on different thread
		new PageRetriever(v, index).start();
		
		return v;
	}
	
	protected void updateViewForUser(View v, boolean loading,
			boolean pageNotFound){
		TextView tv = (TextView)v.findViewById(R.id.txtPageNotFound);
		HighlightingImageView iv = (HighlightingImageView)v.findViewById(R.id.page_image);
		
		if ((loading) || (pageNotFound)){
			if (QuranSettings.getInstance().isNightMode()) {
				tv.setTextColor(Color.WHITE);
			}
			tv.setText(loading? R.string.pageLoading : R.string.pageNotFound);
			if (!loading && mHasBeenOutOfMemory != null && mHasBeenOutOfMemory)
				tv.setText(R.string.couldNotLoadPage);
			tv.setVisibility(View.VISIBLE);
			iv.setVisibility(View.GONE);
		}
		else {
			iv.adjustNightMode();
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
        
    	boolean outOfMemory = false;
        // Bitmap not found in cache..
    	if (bitmap == null){
        	String filename = mContext.getPageFileName(page);
        	try {
        		bitmap = QuranUtils.getImageFromSD(filename);
        	}
        	catch (OutOfMemoryError oe){
        		bitmap = null;
        		outOfMemory = true;
        		mHasBeenOutOfMemory = true;
        	}
        	
        	// Add Bitmap to cache..
        	if (bitmap != null) {
        		cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
        		Log.d(TAG, "page " + page + " added to cache!");
        	} else {
        		if (!outOfMemory){
        			Log.d(TAG, "page " + page + " not found on sdcard");
        			bitmap = QuranUtils.getImageFromWeb(filename);
        		}
        		
        		if (bitmap != null)
            		cache.put("page_" + page, new SoftReference<Bitmap>(bitmap));
        		else Log.d(TAG, "page " + page + " could not be fetched " +
        				(outOfMemory? "due to being out of memory" : "from the web"));
        	}
        }
        
        return bitmap;
    }

	public void setContext(PageViewQuranActivity context,
			QuranPageCurlView quranPage) {
		mContext = context;
		mInflater = LayoutInflater.from(context);
		mQuranPage = quranPage;
		mQuranPage.setOnPageFlipListener(this);	
	}
	
	private class PageRetriever extends Thread {
		View v;
		int index;
		
		public PageRetriever(View v, int index){
			this.index = index;
			this.v =  v;
		}
		
		@Override
		public void run() {
			Bitmap bitmap = getBitmap(index);
			if (bitmap == null && mHasBeenOutOfMemory != null && mHasBeenOutOfMemory){
				android.util.Log.d(TAG, "in a second, will try to get page " + index + " again");
				try { Thread.sleep(1000); } catch (InterruptedException ie){ }
				bitmap = getBitmap(index);
			}
			
			((Activity)mContext).runOnUiThread(new PageDisplayer(bitmap, v, index));
			
			//clear for GC
			v = null;
		}
	}
	
	class PageDisplayer implements Runnable {
		private Bitmap bitmap;
		private View v;
		private int index;
		
		public PageDisplayer(Bitmap bitmap, View v, int index){
			this.bitmap = bitmap;
			this.v = v;
			this.index = index;
		}
		
		public void run(){
			ImageView iv = (ImageView)v.findViewById(R.id.page_image);
			if (bitmap == null) {
	        	Log.d(TAG, "Page not found: " + index);
	        	updateViewForUser(v, false, true);
	        } else {
	        	iv.setImageBitmap(bitmap);
	        	mQuranPage.refresh();
	        	updateViewForUser(v, false, false);
				if (QuranSettings.getInstance().isDisplayMarkerPopup())
					displayMarkerPopup();
	        }
			
			//clear for GC
			iv = null;
			bitmap = null;
			v = null;
		}
	}
}
