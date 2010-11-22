package com.quran.labs.androidquran;

import java.text.NumberFormat;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.quran.labs.androidquran.common.GestureQuranActivity;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranUtils;

public class ExpViewActivity extends GestureQuranActivity {
	private boolean inReadingMode = false;
	private ImageView imageView = null;
	private TextView titleText = null;
	private SeekBar seekBar = null;
	
	private int page = 100;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.quran_exp);
		imageView = (ImageView)findViewById(R.id.pageview);
        titleText = (TextView)findViewById(R.id.pagetitle);
        seekBar = (SeekBar)findViewById(R.id.suraSeek);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (fromUser)
					titleText.setText(QuranInfo.getPageTitle(progress+1));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (seekBar.getProgress() != page){
					page = seekBar.getProgress();
					renderPage();
				}
			}
		});
        
		gestureDetector = new GestureDetector(new QuranGestureDetector());
		
		renderPage();
		toggleMode();
	}
	
	private String getPageFileName() {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(page) + ".png";
	}
	
	private void renderPage(){
		String filename = getPageFileName();
		Bitmap bitmap = QuranUtils.getImageFromSD(filename);
		imageView.setImageBitmap(bitmap);
		
		seekBar.setProgress(page);
		titleText.setText(QuranInfo.getPageTitle(page));
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
	        
	        seekBar.setVisibility(TextView.VISIBLE);
	        titleText.setVisibility(TextView.VISIBLE);
		}
		else {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        
	        seekBar.setVisibility(TextView.INVISIBLE);
	        titleText.setVisibility(TextView.INVISIBLE);
		}
		
		inReadingMode = !inReadingMode;
	}
	
	@Override
	public void goToNextPage() {
		if (page < ApplicationConstants.PAGES_LAST){
			page++;
			renderPage();
		}
	}

	@Override
	public void goToPreviousPage() {
		if (page > ApplicationConstants.PAGES_FIRST){
			page--;
			renderPage();
		}
	}

}
