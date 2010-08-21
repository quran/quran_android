package com.quran.labs.androidquran;

import java.text.NumberFormat;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.AsyncTask.Status;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Toast;

import com.quran.labs.androidquran.common.ApplicationConstants;
import com.quran.labs.androidquran.common.QuranInfo;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

public class QuranViewActivity extends Activity implements AnimationListener {

	private int page;
	private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    // Duration in MS
    private static final int ANIMATION_DURATION = 500;
	private static final int CONTEXT_MENU_REMOVE = 0;
	private static final int CONTEXT_MENU_ADD = 1;
    private GestureDetector gestureDetector;
    private AsyncTask<?, ?, ?> currentTask;
    private float pageWidth, pageHeight;
    private QuranScreenInfo qsi;
    private QuranImageView imageView;
	private ImageView bgImageView;
	private boolean animate;
	private boolean rightTransitionSwap;
	private ScrollView scrollView;
	private Bitmap bitmap;
	private QuranMenuListener qml;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		qml = new QuranMenuListener(this);
		adjustDisplaySettings();
		initializeViewElements();
		initializeQsi();
		loadPageState(savedInstanceState);
		registerListeners();
		pageWidth = 0;
		pageHeight = 0;
		animate = false;
		showPage();
	}
	
	private void adjustDisplaySettings() {
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		if (QuranSettings.getInstance().isFullScreen()) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			if (!QuranSettings.getInstance().isShowClock()) {
				getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
		}				
	}
	
	private void initializeViewElements() {
		setContentView(R.layout.quran_view);
		imageView = (QuranImageView)findViewById(R.id.pageview);
		imageView.setKeepScreenOn(QuranSettings.getInstance().isKeepScreenOn());
		bgImageView = (ImageView)findViewById(R.id.bgPageview);
		scrollView = (ScrollView)findViewById(R.id.pageScrollView);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.option_menu, menu);	  
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		qml.onMenuItemSelected(featureId, item);
		return super.onMenuItemSelected(featureId, item);
	}
	
	@Override
    protected Dialog onCreateDialog(int id){
		if (id == ApplicationConstants.JUMP_DIALOG){
    		Dialog dialog = new QuranJumpDialog(this);
    		dialog.setOnCancelListener(new OnCancelListener(){
    			public void onCancel(DialogInterface dialog) {
    				QuranJumpDialog dlg = (QuranJumpDialog)dialog;
    				Integer page = dlg.getPage();
    				removeDialog(ApplicationConstants.JUMP_DIALOG);
    				if (page != null) {
    					animate = false;
    					QuranViewActivity.this.page = page.intValue();
    					showPage();
    				}
    			}

    		});
    		return dialog;
    	}
    	return null;
    }
	
	private void registerListeners() {	
		imageView.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
				int menuType;
				int menuTitle;
				if (BookmarksManager.getInstance().contains(page)) {
					menuType = CONTEXT_MENU_REMOVE;
					menuTitle = R.string.menu_bookmarks_remove;
				} else {
					menuType = CONTEXT_MENU_ADD;
					menuTitle = R.string.menu_bookmarks_add;
				}
				menu.add(0, menuType, 0, menuTitle);
			}
		});
		
		gestureDetector = new GestureDetector(new QuranGestureDetector());
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch(item.getItemId()) {
			case CONTEXT_MENU_ADD:
			case CONTEXT_MENU_REMOVE:
				SharedPreferences preferences = getSharedPreferences(ApplicationConstants.PREFERNCES, 0);
				boolean added = BookmarksManager.toggleBookmarkState(page, preferences);
				int msgId = added ? R.string.menu_bookmarks_saved : R.string.menu_bookmarks_removed;
				Toast.makeText(getApplicationContext(), msgId, Toast.LENGTH_SHORT).show();
			break;
		}
		return true;
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
	
	// thanks to codeshogun's blog post for this
	// http://www.codeshogun.com/blog/2009/04/16/how-to-implement-swipe-action-in-android/
	class QuranGestureDetector extends SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
			if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH)
				return false;
			// previous page swipe
			if ((e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE) && 
			    (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY)){
				goToPreviousPage();
			}
			else if ((e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE) &&
				(Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY)){
				goToNextPage();
			}
			
			return false;
		}
		
		@Override
		public boolean onDoubleTap(MotionEvent e){
			handleDoubleTap(e.getX(), e.getY());
			return false;
		}
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
	
	private void goToNextPage() {
		animate = true;
		if (page < ApplicationConstants.PAGES_LAST) {
			page++;
			rightTransitionSwap = true;
			showPage();
		}
	}

	private void goToPreviousPage() {
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
		QuranSettings.save(getSharedPreferences(ApplicationConstants.PREFERNCES, 0));
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
}
