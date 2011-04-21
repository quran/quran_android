package com.quran.labs.androidquran;

import java.text.NumberFormat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.QuranImageView;

public class QuranViewActivity extends GestureQuranActivity implements AnimationListener {

	private int page;

    // Duration in MS
    private static final int ANIMATION_DURATION = 500;
    private AsyncTask<?, ?, ?> currentTask;
    private float pageWidth, pageHeight;
    private QuranScreenInfo qsi;
    private QuranImageView imageView;
	private ImageView bgImageView;
	private boolean animate;
	private boolean rightTransitionSwap;
	private ScrollView scrollView;
	private Bitmap bitmap;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		initializeViewElements();
		initializeQsi();
		loadPageState(savedInstanceState);
		registerListeners();
		pageWidth = 0;
		pageHeight = 0;
		animate = false;
		showPage();
	}
	
	private void initializeViewElements() {
		setContentView(R.layout.quran_view);
		imageView = (QuranImageView)findViewById(R.id.pageview);
		imageView.setKeepScreenOn(QuranSettings.getInstance().isKeepScreenOn());
		bgImageView = (ImageView)findViewById(R.id.bgPageview);
		scrollView = (ScrollView)findViewById(R.id.pageScrollView);
	}
	

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == ApplicationConstants.TRANSLATION_VIEW_CODE){
			Integer lastPage = data.getIntExtra("page",
					QuranSettings.getInstance().getLastPage());
			if (lastPage != null && lastPage != ApplicationConstants.NO_PAGE_SAVED)
				jumpTo(lastPage);
		} else if (requestCode == ApplicationConstants.SETTINGS_CODE) {
			// Reason to finish is that the fullscreen settings requires to call 
			// requestWindowFeature() a second time after setContentView() is called.  
			// This causes runtimeexceptions in android. The ideal way to fix this  
			// is that the activity has to be restarted. 
			//
			//If you know of a way to restart the activity smoothly, then 
			// you can remove this and call the restart method instead. Otherwise, leave 
			// it be and make dua! 
			finish();
		}
	}
	
	private void registerListeners() {		
		gestureDetector = new GestureDetector(new QuranGestureDetector(true));
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch(item.getItemId()) {
			case R.id.menu_item_bookmarks:
				boolean exists = BookmarksManager.getInstance().contains(page);
				item.getSubMenu().findItem(R.id.menu_item_bookmarks_add).setVisible(!exists);
				item.getSubMenu().findItem(R.id.menu_item_bookmarks_remove).setVisible(exists);
			break;
			case R.id.menu_item_bookmarks_add:
			case R.id.menu_item_bookmarks_remove:
				boolean added = BookmarksManager.toggleBookmarkState(page, prefs);
				int msgId = added ? R.string.menu_bookmarks_saved : R.string.menu_bookmarks_removed;
				Toast.makeText(getApplicationContext(), msgId, Toast.LENGTH_SHORT).show();
			break;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	
	private void loadPageState(Bundle savedInstanceState) {
		page = savedInstanceState != null ? savedInstanceState.getInt("page") : ApplicationConstants.PAGES_FIRST;
		if (page == ApplicationConstants.PAGES_FIRST){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : ApplicationConstants.PAGES_FIRST;
		}
	}
	
	private void initializeQsi() {
		WindowManager w = getWindowManager();
		Display d = w.getDefaultDisplay();
		int width = d.getWidth();
		int height = d.getHeight();
		Log.d("quran", "screen size: width [" + width + "], height: [" + height + "]");
		QuranScreenInfo.initialize(width, height); 
		qsi = QuranScreenInfo.getInstance();
	}
	
	@Override
	protected void onDestroy(){
		super.onDestroy();
		if ((currentTask != null) && (currentTask.getStatus() == Status.RUNNING))
			currentTask.cancel(true);
	}

	public void handleDoubleTap(float x, float y){
		// just in case...
		if ((pageWidth == 0) || (pageHeight == 0) || (qsi == null)) return;
		
		float xScale = pageWidth / imageView.getWidth();
		float yScale = pageHeight / imageView.getHeight();
		
		float scrollY = 0;
		if (scrollView != null && scrollView.isEnabled()) {
			scrollY = scrollView.getScrollY();
		} else {
			// take into account offset from the top of the screen
			x = x - (qsi.getWidth() - imageView.getWidth());
			y = y - (qsi.getHeight() - imageView.getHeight());
		}
		
		x = x * xScale;
		y = (y * yScale) + scrollY;
		Log.d("quran_view", "position of dbl tap: " + x + ", " + y);
	}
	
	public void goToNextPage() {
		animate = true;
		if (page < ApplicationConstants.PAGES_LAST) {
			page++;
			rightTransitionSwap = true;
			showPage();
		}
	}

	public void goToPreviousPage() {
		animate = true;
		if (page != ApplicationConstants.PAGES_FIRST) {
			page--;
			rightTransitionSwap = false;
			showPage();
		}
	}
	
	private String getPageFileName() {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(page) + ".png";
	}

	private void showPage(){
		setTitle(QuranInfo.getPageTitle(page));
		
		String filename = getPageFileName();
		Bitmap bitmap = QuranUtils.getImageFromSD(filename);
		if (bitmap == null){
			Log.d("quran_view", "need to download " + filename);
			if (currentTask != null) {
				currentTask.cancel(true);	
			}
			setProgressBarIndeterminateVisibility(true);
			currentTask = new DownloadPageTask().execute(filename);
		} else  {
			showBitmap(bitmap);
		}
	}
		
	private void doneDownloadingPage(Bitmap bitmap){
		Log.d("quran_view", "done downloading page");
		setProgressBarIndeterminateVisibility(false);
		showBitmap(bitmap);
	}
	
	private void showBitmap(Bitmap bitmap){
		this.bitmap = bitmap;
		if (bitmap == null) {
			setContentView(R.layout.quran_error);
			return;
		}
		
		QuranSettings.getInstance().setLastPage(page);
		QuranSettings.save(prefs);
		pageWidth = bitmap.getWidth();
		pageHeight = bitmap.getHeight();
		
		if (animate) {
			bgImageView.setImageBitmap(bitmap);
			animateSwappingPages();
		} else {
			imageView.setImageBitmap(bitmap);
		}
		
		resetScroller();
	}
	
	private void resetScroller() {
		if (scrollView != null && scrollView.isEnabled()) {
			scrollView.post(new Runnable(){
				public void run(){ scrollView.scrollTo(0, 0); }
			});	
		}
	}

	private class DownloadPageTask extends AsyncTask<String, Void, Bitmap> {

		@Override
		protected Bitmap doInBackground(String... arg0) {
			String filename = arg0[0];
			return QuranUtils.getImageFromWeb(filename);
		}
		
		@Override
		protected void onPostExecute(Bitmap b){
			currentTask = null;
			doneDownloadingPage(b);
		}
		
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			goToNextPage();
		}
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			goToPreviousPage();
		}
		return super.onKeyDown(keyCode, event);
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState){
		super.onSaveInstanceState(outState);
		outState.putInt("page", page);
	}
	
	@Override
	protected void onPause(){
		super.onPause();
		if (imageView != null)
			imageView.setKeepScreenOn(false);
	}
	
	@Override
	protected void onResume(){
		super.onResume();
        QuranSettings.load(prefs);
		BookmarksManager.load(prefs);
        
		animate = false;
		showPage();
		imageView.setKeepScreenOn(QuranSettings.getInstance().isKeepScreenOn());
	}
	
	private void animateSwappingPages() {
		// In case
		if (!animate) {
			imageView.setImageBitmap(bitmap);
			resetScroller();
			return;
		}
		
		if (qsi == null)
			initializeQsi();
		animate = false;
		int translationWidth = qsi.getWidth();
		translationWidth = rightTransitionSwap ? translationWidth : -translationWidth;
		
		TranslateAnimation t = new TranslateAnimation(0, translationWidth, 0, 0);
        t.setStartOffset(0);
        t.setDuration(ANIMATION_DURATION);
        t.setFillAfter(false);
        t.setFillBefore(false);
        
        t.setAnimationListener(this);
        imageView.startAnimation(t);
	}

	public void onAnimationEnd(Animation animation) {
		imageView.setImageBitmap(bitmap);
	}

	public void onAnimationRepeat(Animation animation) {
		
	}

	public void onAnimationStart(Animation animation) {

	}
	
	@Override
	public void jumpTo(int page) {
		this.page = page;
		animate = false;
		showPage();
	}
}
