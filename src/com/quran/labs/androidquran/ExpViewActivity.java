package com.quran.labs.androidquran;

import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

import com.quran.labs.androidquran.common.GestureQuranActivity;

public class ExpViewActivity extends GestureQuranActivity {
	private boolean inReadingMode = true;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		// setContentView(R.layout.quran_view);
		gestureDetector = new GestureDetector(new QuranGestureDetector());
	}
	
	@Override
	public void handleSingleTap(MotionEvent e){
		// TODO: see where the click happens before deciding
		toggleMode();
	}
	
	public void toggleMode(){
		if (inReadingMode){
	        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		else {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		}
		
		inReadingMode = !inReadingMode;
	}
	
	@Override
	public void goToNextPage() {		
	}

	@Override
	public void goToPreviousPage() {		
	}

}
