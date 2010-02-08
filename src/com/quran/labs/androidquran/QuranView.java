package com.quran.labs.androidquran;

import java.text.NumberFormat;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.ImageView;

public class QuranView extends Activity {

	private int page;
	private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    private GestureDetector gestureDetector;
	
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.quran_view);
		
		page = savedInstanceState != null?
				savedInstanceState.getInt("page") : 1;
		if (page == 1){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : 1;
		}
		
		gestureDetector = new GestureDetector(new QuranGestureDetector());
		showSura();
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event){
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
				if (page != 1){
					page--;
					showSura();
				}
			}
			else if ((e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE) &&
				(Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY)){
				if (page < 604){
					page++;
					showSura();
				}
			}
			
			return false;
		}
	}
	
	private void showSura(){
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		
		String filename = "page" + nf.format(page) + ".png";

		Drawable drawable = QuranUtils.getImageFromSD(filename);
		if (drawable == null)
			drawable = QuranUtils.getImageFromWeb(filename);
		if (drawable == null){
			setContentView(R.layout.quran_error);
			return;
		}
		
		ImageView imageView = (ImageView)findViewById(R.id.pageview);
		imageView.setImageDrawable(drawable);
		String title = "Quran, page " + page +
			" - [Surat " + QuranInfo.getSuraNameFromPage(page) + "]";
		setTitle(title);
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			if (page != 604){
				page++;
				showSura();
			}
		}
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			if (page != 1){
				page--;
				showSura();
			}
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
	}
	
	@Override
	protected void onResume(){
		super.onResume();
		showSura();
	}
}
