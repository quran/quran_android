package com.quran.labs.androidquran.common;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranSettings;

public abstract class GestureQuranActivity extends BaseQuranActivity {
    protected GestureDetector gestureDetector;
    
	private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    
    @Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
        QuranSettings.load(prefs);
		BookmarksManager.load(prefs);
		adjustDisplaySettings();
    }
    
	// thanks to codeshogun's blog post for this
	// http://www.codeshogun.com/blog/2009/04/16/how-to-implement-swipe-action-in-android/
	public class QuranGestureDetector extends SimpleOnGestureListener {
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
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
	
	public abstract void goToNextPage();
	public abstract void goToPreviousPage();
	public boolean handleSingleTap(MotionEvent e){ return false; }
	public boolean handleDoubleTap(MotionEvent e){ return false; }
	
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
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		if (QuranSettings.getInstance().isFullScreen()) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			if (!QuranSettings.getInstance().isShowClock()) {
				getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
		}	
		
		adjustActivityOrientation();
	}
}
