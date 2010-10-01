package com.quran.labs.androidquran.common;

import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

public abstract class GestureQuranActivity extends BaseQuranActivity {
    protected GestureDetector gestureDetector;
    
	private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    
	// thanks to codeshogun's blog post for this
	// http://www.codeshogun.com/blog/2009/04/16/how-to-implement-swipe-action-in-android/
	public class QuranGestureDetector extends SimpleOnGestureListener {
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
	}
	
	public abstract void goToNextPage();
	public abstract void goToPreviousPage();
	
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
}
