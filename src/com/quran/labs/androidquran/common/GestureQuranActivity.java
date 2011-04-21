package com.quran.labs.androidquran.common;

import java.text.NumberFormat;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnClickListener;
import android.widget.Gallery;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranSettings;

public abstract class GestureQuranActivity extends BaseQuranActivity {
    protected GestureDetector gestureDetector;
    
	protected static final int SWIPE_MIN_DISTANCE = 120;
	protected static final int SWIPE_MAX_OFF_PATH = 250;
	protected static final int SWIPE_THRESHOLD_VELOCITY = 200;
	protected static final int BOOKMARK_SAFE_REGION = 15;
    
    protected ImageView btnBookmark = null;
    protected boolean inReadingMode = false;
    protected Gallery gallery = null;
    protected SeekBar seekBar = null;
    protected TextView titleText = null;
    protected ViewGroup expLayout = null;
    protected int width = 0;
    protected int currentPage;
    protected QuranGalleryAdapter galleryAdapter = null;
    
    @Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.quran_exp);
		
		WindowManager manager = getWindowManager();
		Display display = manager.getDefaultDisplay();
		width = display.getWidth();

		initComponents();

		gestureDetector = new GestureDetector(new QuranGestureDetector());
		
		BookmarksManager.load(prefs);
		adjustDisplaySettings();

		currentPage = loadPageState(savedInstanceState);

		toggleMode();
    }
    
    @Override
    public void onLowMemory() {
    	super.onLowMemory();
    	galleryAdapter.emptyCache();
    }
    
    protected abstract void initGalleryAdapter();
    
    protected void initComponents() {
    	initGalleryAdapter();
		expLayout = (ViewGroup) findViewById(R.id.expLayout);
		
		gallery = (Gallery) findViewById(R.id.gallery);
		gallery.setAdapter(galleryAdapter);
		gallery.setAnimationDuration(0);
		gallery.setSpacing(25);

		titleText = (TextView) findViewById(R.id.pagetitle);
		//toolbar = (View) findViewById(R.id.toolbar);
//		btnLockOrientation = (ImageView) findViewById(R.id.btnLockOrientation);
//		btnLockOrientation.setOnClickListener(new OnClickListener() {
//			public void onClick(View v) {
//				QuranSettings qs = QuranSettings.getInstance();
//				qs.setLockOrientation(!qs.isLockOrientation());
//				QuranSettings.save(prefs);
//				adjustLockView();
//			}
//		});

		btnBookmark = (ImageView) findViewById(R.id.btnBookmark);
		btnBookmark.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				BookmarksManager.toggleBookmarkState(QuranSettings.getInstance().getLastPage(), prefs);
				adjustBookmarkView();
			}
		});

		seekBar = (SeekBar) findViewById(R.id.suraSeek);
		seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser)
					titleText.setText(QuranInfo.getPageTitle(ApplicationConstants.PAGES_LAST - progress));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (seekBar.getProgress() != gallery.getSelectedItemPosition()) {
					renderPage(seekBar.getProgress());
				}
			}
		});
	}
    
	protected void goToNextPage() {
		int position = gallery.getSelectedItemPosition();
		if (position > 0)
			renderPage(position - 1);
	}

	protected void goToPreviousPage() {
		int position = gallery.getSelectedItemPosition();
		if (position < ApplicationConstants.PAGES_LAST - 1)
			renderPage(position + 1);
	}
	
	protected void renderPage(int position){
		gallery.setSelection(position, true);
		adjustBookmarkView();
	}
	
	protected void adjustBookmarkView() {
		if (BookmarksManager.getInstance().contains(QuranSettings.getInstance().getLastPage())) {
			btnBookmark.setImageResource(R.drawable.bookmarks);
		} else {
			btnBookmark.setImageResource(R.drawable.remove_bookmark);
		}
	}
        
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d("QuranAndroid", "KeyDown");
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			goToNextPage();
		}
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			goToPreviousPage();
		}

		return super.onKeyDown(keyCode, event);
	}
	
	// thanks to codeshogun's blog post for this
	// http://www.codeshogun.com/blog/2009/04/16/how-to-implement-swipe-action-in-android/
	public class QuranGestureDetector extends SimpleOnGestureListener {		
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			return false;
		}
		
		@Override
		public boolean onDoubleTap(MotionEvent e){
			return handleDoubleTap(e);
		}
		
		@Override
		public boolean onSingleTapConfirmed(MotionEvent e){
			return handleSingleTap(e);
		}
	}
	
	public boolean handleDoubleTap(MotionEvent e) {
		return false;
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event){
		return gestureDetector.onTouchEvent(event);
	}
	
	// this function lets this activity handle the touch event before the ScrollView
	@Override
	public boolean dispatchTouchEvent(MotionEvent event){
		super.dispatchTouchEvent(event);
		return gestureDetector.onTouchEvent(event);
	}
	
	protected void adjustActivityOrientation() {
		if (QuranSettings.getInstance().isLockOrientation()) {
			if (QuranSettings.getInstance().isLandscapeOrientation()) 
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else 
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}
	}
	
	protected void adjustDisplaySettings() {
		if (QuranSettings.getInstance().isFullScreen()) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			if (!QuranSettings.getInstance().isShowClock()) {
				getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
		}	
		
		adjustActivityOrientation();
	}
	
	protected String getPageFileName(int p) {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(p) + ".png";
	}
	
	protected int loadPageState(Bundle savedInstanceState){
		int page = savedInstanceState != null ? savedInstanceState.getInt("page") : ApplicationConstants.PAGES_FIRST;
		if (page == ApplicationConstants.PAGES_FIRST){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : ApplicationConstants.PAGES_FIRST;
		}
		
		return page;
	}
	
	public boolean handleSingleTap(MotionEvent e){
		Log.d("exp_v", "in handle single tap");
		int sliceWidth = (int)(0.2 * width);
		
		// Skip bookmark region
		int bookmarkRegionY = btnBookmark.getTop() + btnBookmark.getHeight() + BOOKMARK_SAFE_REGION;
		int bookmarkRegionX = btnBookmark.getLeft() + btnBookmark.getWidth() + BOOKMARK_SAFE_REGION;
		if (e.getY() < bookmarkRegionY && e.getX() < bookmarkRegionX)
			return true;
		
		if (e.getX() < sliceWidth)
			goToNextPage();
		else if (e.getX() > (width - sliceWidth))
			goToPreviousPage();
		else toggleMode();
		
		return true;
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		expLayout.setKeepScreenOn(QuranSettings.getInstance().isKeepScreenOn());
		Log.d("QuranAndroid", "Screen on");
		adjustActivityOrientation();
		//currentPage = QuranSettings.getInstance().getLastPage();
		renderPage(ApplicationConstants.PAGES_LAST - currentPage);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		expLayout.setKeepScreenOn(false);
		Log.d("QuranAndroid","Screen off");
	}
	
	protected void updatePageInfo(int position){
		titleText.setText(QuranInfo.getPageTitle(ApplicationConstants.PAGES_LAST - position));
		seekBar.setProgress(position);
	}
	
	protected void toggleMode(){
		Log.d("exp_v", "in toggle mode");
		if (inReadingMode){
	        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        
	        //toolbar.setVisibility(View.VISIBLE);
	        seekBar.setVisibility(TextView.VISIBLE);
	        titleText.setVisibility(TextView.VISIBLE);
	        //btnLockOrientation.setVisibility(View.VISIBLE);
	        btnBookmark.setVisibility(View.VISIBLE);
	        //adjustLockView();
			adjustBookmarkView();
		}
		else {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        
	        //toolbar.setVisibility(View.INVISIBLE);
	        seekBar.setVisibility(TextView.INVISIBLE);
	        titleText.setVisibility(TextView.INVISIBLE);
	        //btnLockOrientation.setVisibility(View.INVISIBLE);
	        btnBookmark.setVisibility(View.INVISIBLE);
		}
		
		inReadingMode = !inReadingMode;
	}
}
