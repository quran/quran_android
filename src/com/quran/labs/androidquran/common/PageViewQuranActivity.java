package com.quran.labs.androidquran.common;

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

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.QuranPageCurlView;

public abstract class PageViewQuranActivity extends BaseQuranActivity {
	private static final String TAG = "BaseQuranActivity";

	protected ImageView btnBookmark = null;
    protected boolean inReadingMode = false;
    protected SeekBar seekBar = null;
    protected TextView titleText = null;
    protected ViewGroup expLayout = null;
    protected int width = 0;
    protected QuranPageCurlView quranPageCurler = null;
    protected QuranPageFeeder quranPageFeeder;
    
	protected abstract void initQuranPageFeeder();
	
	protected void initComponents() {
		expLayout = (ViewGroup) findViewById(R.id.expLayout);
		
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
				if (fromUser)
					titleText.setText(ArabicStyle.reshape(
							QuranInfo.getPageTitle(
								ApplicationConstants.PAGES_LAST - progress)));
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
    
	protected void goToNextPage() {
		quranPageFeeder.goToNextpage();
	}

	protected void goToPreviousPage() {
		quranPageFeeder.goToPreviousPage();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		Log.d(TAG, "KeyDown");
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT){
			goToNextPage();
		}
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
			goToPreviousPage();
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
		if (BookmarksManager.getInstance().contains(
				QuranSettings.getInstance().getLastPage())) {
			btnBookmark.setImageResource(R.drawable.bookmarks);
		} else {
			btnBookmark.setImageResource(R.drawable.remove_bookmark);
		}
	}
	
	protected void adjustDisplaySettings() {
		if (QuranSettings.getInstance().isFullScreen()) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
			if (!QuranSettings.getInstance().isShowClock()) {
				getWindow().setFlags(
					WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
			}
		}	
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
	        
	        seekBar.setVisibility(TextView.VISIBLE);
	        titleText.setVisibility(TextView.VISIBLE);
	        btnBookmark.setVisibility(View.VISIBLE);
			adjustBookmarkView();
			updatePageInfo();
		}
		else {
			getWindow().addFlags(
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().clearFlags(
	        		WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        
	        seekBar.setVisibility(TextView.INVISIBLE);
	        titleText.setVisibility(TextView.INVISIBLE);
	        btnBookmark.setVisibility(View.INVISIBLE);
		}
		
		inReadingMode = !inReadingMode;
	}
	
	
	protected void updatePageInfo(int position){
		Log.d(TAG, "Update page info: " + position);
		titleText.setText(ArabicStyle.reshape(
				QuranInfo.getPageTitle(position)));
		seekBar.setProgress(ApplicationConstants.PAGES_LAST - position);
	}
	
	private void updatePageInfo() {
		updatePageInfo(quranPageFeeder.getCurrentPagePosition());
		QuranSettings.getInstance().setLastPage(
				quranPageFeeder.getCurrentPagePosition());
	}
}
