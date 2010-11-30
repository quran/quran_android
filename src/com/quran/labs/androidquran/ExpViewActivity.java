package com.quran.labs.androidquran;

import java.text.NumberFormat;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Display;
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
	
	private int page = 1;
	private int width = 0;
	
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
		
		WindowManager manager = getWindowManager();
		Display display = manager.getDefaultDisplay();
		width = display.getWidth();
		
		loadState(savedInstanceState);
		renderPage();
		toggleMode();
	}
	
	private void loadState(Bundle savedInstanceState){
		page = savedInstanceState != null ? savedInstanceState.getInt("page") : ApplicationConstants.PAGES_FIRST;
		if (page == ApplicationConstants.PAGES_FIRST){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : ApplicationConstants.PAGES_FIRST;
		}
	}
	
	private String getPageFileName() {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(page) + ".png";
	}
	
	private void renderPage(){
		String filename = getPageFileName();
		Bitmap bitmap = QuranUtils.getImageFromSD(filename);
		// TODO: handle null case (download page)
		imageView.setImageBitmap(bitmap);
		
		seekBar.setProgress(page);
		titleText.setText(QuranInfo.getPageTitle(page));
	}
	
	@Override
	public void handleSingleTap(MotionEvent e){
		int sliceWidth = (int)(0.2 * width);
		if (e.getX() < sliceWidth)
			goToNextPage();
		else if (e.getX() > (width - sliceWidth))
			goToPreviousPage();
		else toggleMode();
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
