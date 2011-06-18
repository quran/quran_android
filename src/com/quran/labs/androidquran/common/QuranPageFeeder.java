package com.quran.labs.androidquran.common;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.quran.labs.androidquran.QuranViewActivity;
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
	
	private QuranViewActivity mContext;
	private QuranPageCurlView mQuranPage;
	
	private LayoutInflater mInflater;
	private int mPageLayout;
	private int mCurrentPageNumber;
	
	public QuranPageFeeder(QuranViewActivity context, QuranPageCurlView quranPage, int page_layout) {
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
		
		// TODO: do i need to invalidate to redraw?
		mQuranPage.refresh(true);
		mQuranPage.refresh(false);
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
}
