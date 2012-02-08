package com.quran.labs.androidquran.common;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.markupartist.android.widget.ActionBar;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.QuranPageCurlView;


public abstract class PageViewQuranActivity extends InternetActivity {
	protected static final String ACTION_BOOKMARK = "ACTION_BOOKMARK";

	private static final String TAG = "BaseQuranActivity";

	protected ImageView btnBookmark = null;
	
    protected boolean inReadingMode = false;
    protected SeekBar seekBar = null;
    protected TextView titleText = null;
    protected ViewGroup expLayout = null;
    protected QuranPageCurlView quranPageCurler = null;
    protected QuranPageFeeder quranPageFeeder;
	protected ActionBar actionBar;
	protected ViewGroup bottomToolbar;
	private boolean nightMode;
	
	protected abstract void initQuranPageFeeder();

	protected void requestWindowFeatures() {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Request window feautres should be called before setting the layout
		requestWindowFeatures();

		// Adjust display settings
		adjustDisplaySettings();
		
		setContentView(R.layout.quran_exp);
		
		// reinitialize quran screen info if it was lost due to memory
		if (QuranScreenInfo.getInstance() == null){
			android.util.Log.d(TAG, "reinitializing QuranScreenInfo...");
			initializeQuranScreen();
		}
		
		// retrieve saved configurations
		loadLastNonConfigurationInstance();
		
		// initialize scree componnets
		initComponents();
		
		// get action bar
		actionBar = (ActionBar) findViewById(R.id.actionbar);
		addActions();
		
		// go to page
		BookmarksManager.load(prefs);
		int page = loadPageState(savedInstanceState);
		quranPageFeeder.jumpToPage(page);

		toggleMode();
		nightMode = QuranSettings.getInstance().isNightMode();
	}
	
	protected void initComponents() {
		expLayout = (ViewGroup) findViewById(R.id.expLayout);
		bottomToolbar = (ViewGroup) findViewById(R.id.bottomToolbar);
		
		if (quranPageCurler == null){
			quranPageCurler = (QuranPageCurlView)findViewById(R.id.gallery);
			quranPageCurler.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					toggleMode();
				}
			});
		}
		
		initQuranPageFeeder();
		
		titleText = (TextView) findViewById(R.id.pagetitle);
		titleText.setTypeface(ArabicStyle.getTypeface());
		
		btnBookmark = (ImageView) findViewById(R.id.btnBookmark);
		btnBookmark.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				BookmarksManager.toggleBookmarkState(
						QuranSettings.getInstance().getLastPage(), prefs);
				adjustBookmarkView();
			}
		});
		
		seekBar = (SeekBar) findViewById(R.id.suraSeek);
		seekBar.setOnSeekBarChangeListener(
				new SeekBar.OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser) {
					int page = ApplicationConstants.PAGES_LAST - progress;
					titleText.setText(ArabicStyle.reshape(QuranInfo.getPageTitle(page)));
					adjustBookmarkView(page);
				}
				
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (seekBar.getProgress() !=
					quranPageFeeder.getCurrentPagePosition()) {
					quranPageFeeder.jumpToPage(
							ApplicationConstants.PAGES_LAST -
								seekBar.getProgress());
				}
			}
		});
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		// Always initialize Quran Screen on start so as to be able to retrieve images
		// Error cause: Gallery Adapter was unable to retrieve images from SDCard as QuranScreenInfo
		// was cleared after long sleep..
		initializeQuranScreen();
	}
	
	@Override
	public void jumpTo(int page) {
		quranPageFeeder.jumpToPage(page);
		updatePageInfo();
	}
	    
	protected void goToNextPage() {
		if (quranPageFeeder.mCurrentPageNumber < ApplicationConstants.PAGES_LAST)
			quranPageFeeder.goToNextpage();
	}

	protected void goToPreviousPage() {
		if (quranPageFeeder.mCurrentPageNumber > ApplicationConstants.PAGES_FIRST)
			quranPageFeeder.goToPreviousPage();
	}
	
	protected void scrollPageDown() {
		quranPageFeeder.ScrollDown();
	}
	
	protected void scrollPageUp() {
		quranPageFeeder.ScrollUp();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d(TAG, "KeyDown");
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			goToNextPage();
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			goToPreviousPage();
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			scrollPageDown();
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP){
			scrollPageUp();
		}

		return super.onKeyDown(keyCode, event);
	}
	
	protected void adjustActivityOrientation() {
		if (QuranSettings.getInstance().isLockOrientation()) {
			// TODO - don't call setRequestedOrientation here unless we are
			// in the wrong orientation...
			if (QuranSettings.getInstance().isLandscapeOrientation())
				setRequestedOrientation(
						ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else 
				setRequestedOrientation(
						ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}
	
	protected void adjustBookmarkView() {
		adjustBookmarkView(quranPageFeeder.mCurrentPageNumber);		
	}
	
	protected void adjustBookmarkView(int position) {
		int r = BookmarksManager.getInstance().contains(position) ?
					R.drawable.bookmarks : R.drawable.remove_bookmark;
		btnBookmark.setImageResource(r);
	}
	
	protected void adjustDisplaySettings() {
		getWindow().setFlags(
			WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);
	}
	
	protected int loadPageState(Bundle savedInstanceState){
		int page = savedInstanceState != null ?
				savedInstanceState.getInt("lastPage") :
					ApplicationConstants.NO_PAGE_SAVED;
		
		if (page == ApplicationConstants.NO_PAGE_SAVED){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") :
				QuranSettings.getInstance().getLastPage();
			
			// If still no page saved
			if (page == ApplicationConstants.NO_PAGE_SAVED) {
				page = ApplicationConstants.PAGES_FIRST;
			}
		} 
		
		Log.d(TAG, "page: "+ page+" fyi: "+
				QuranSettings.getInstance().getLastPage());
		return page;
	}
	
	protected void toggleMode(){
		Log.d("exp_v", "in toggle mode");
		if (inReadingMode){
	        getWindow().addFlags(
	        		WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().clearFlags(
	        		WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        
	        bottomToolbar.setVisibility(View.VISIBLE);
	        if(actionBar != null)
	        	actionBar.setVisibility(View.VISIBLE);
			adjustBookmarkView();
			updatePageInfo();
		}
		else {
			getWindow().addFlags(
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().clearFlags(
	        		WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        
	        bottomToolbar.setVisibility(View.INVISIBLE);
	        if(actionBar != null)
	        	actionBar.setVisibility(View.INVISIBLE);
		}
		
		inReadingMode = !inReadingMode;
	}
	
	
	protected void updatePageInfo(int position){
		Log.d(TAG, "Update page info: " + position);
		titleText.setText(ArabicStyle.reshape(
				QuranInfo.getPageTitle(position)));
		seekBar.setProgress(ApplicationConstants.PAGES_LAST - position);
		adjustBookmarkView(position);
	}
	
	private void updatePageInfo() {
		updatePageInfo(quranPageFeeder.getCurrentPagePosition());
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		Object [] o = { quranPageFeeder, new Boolean(!inReadingMode) };
		return o;
	}
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("lastPage", QuranSettings.getInstance().getLastPage());
		super.onSaveInstanceState(outState);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		// Restart activity if night mode was changed..
		if (nightMode != QuranSettings.getInstance().isNightMode()) {
			finish();
			startActivity(getIntent());
		}
		expLayout.setKeepScreenOn(QuranSettings.getInstance().isKeepScreenOn());
		Log.d("QuranAndroid", "Screen on");
		adjustActivityOrientation();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		expLayout.setKeepScreenOn(false);
		Log.d("QuranAndroid","Screen off");
	}
	
	protected void loadLastNonConfigurationInstance() {
		Object [] saved = (Object []) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Reading mode");
			inReadingMode = ((Boolean) saved[1]).booleanValue();
		}
	}
	
	protected void addActions() {
		
	}
	
	protected void onNewIntent(Intent intent) {
		
	}
}
