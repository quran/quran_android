package com.quran.labs.androidquran;

import java.text.NumberFormat;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.quran.labs.androidquran.common.BaseQuranActivity;
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.data.ApplicationConstants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.QuranPageCurlView;

public class QuranViewActivity extends BaseQuranActivity {

	private static final String TAG = "QuranViewActivity";
	
	protected ImageView btnBookmark = null;
    protected boolean inReadingMode = false;
    protected SeekBar seekBar = null;
    protected TextView titleText = null;
    protected ViewGroup expLayout = null;
    protected int width = 0;
    
    protected QuranPageCurlView quranPageCurler = null;
    protected QuranPageFeeder quranPageFeeder;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Object [] saved = (Object []) getLastNonConfigurationInstance();
		if (saved != null) {
			Log.d("exp_v", "Adapter retrieved..");
			quranPageFeeder = (QuranPageFeeder) saved[0];
		} 
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// does requestWindowFeature, has to be before setContentView
		adjustDisplaySettings();

		setContentView(R.layout.quran_exp);
		
		WindowManager manager = getWindowManager();
		Display display = manager.getDefaultDisplay();
		width = display.getWidth();

		initComponents();		
		BookmarksManager.load(prefs);
		
		int page = loadPageState(savedInstanceState);
		quranPageFeeder.jumpToPage(page);
		//renderPage(ApplicationConstants.PAGES_LAST - page);

		toggleMode();
	}
	
	@Override
	public Object onRetainNonConfigurationInstance() {
		Object [] o = { quranPageFeeder };
		return o;
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		// Always initialize Quran Screen on start so as to be able to retrieve images
		// Error cause: Gallery Adapter was unable to retrieve images from SDCard as QuranScreenInfo
		// was cleared after long sleep..
		initializeQuranScreen();
	}

	protected void initQuranPageFeeder(){
		if (quranPageFeeder == null) {
			Log.d(TAG, "Quran Feeder instantiated...");
			quranPageFeeder = new QuranPageFeeder(this, quranPageCurler, R.layout.quran_page_layout);
		} else {
			quranPageFeeder.setContext(this, quranPageCurler);
		}
	}
    
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
					titleText.setText(ArabicStyle.reshape(QuranInfo.getPageTitle(ApplicationConstants.PAGES_LAST - progress)));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (seekBar.getProgress() != quranPageFeeder.getCurrentPagePosition()) {
					quranPageFeeder.jumpToPage(ApplicationConstants.PAGES_LAST - seekBar.getProgress());
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
	
	protected void adjustActivityOrientation() {
		if (QuranSettings.getInstance().isLockOrientation()) {
			// TODO - don't call setRequestedOrientation here unless we are in the
			// wrong orientation...
			if (QuranSettings.getInstance().isLandscapeOrientation()) 
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			else 
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else {
			// why is this here?
			// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
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
		
		// onResume will call adjustActivityOrientation
		// adjustActivityOrientation();
	}
	
	public String getPageFileName(int p) {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		return "page" + nf.format(p) + ".png";
	}
	
	protected int loadPageState(Bundle savedInstanceState){
	
		int page = savedInstanceState != null ? savedInstanceState.getInt("lastPage") : ApplicationConstants.NO_PAGE_SAVED;
		
		if (page == ApplicationConstants.NO_PAGE_SAVED){
			Bundle extras = getIntent().getExtras();
			page = extras != null? extras.getInt("page") : QuranSettings.getInstance().getLastPage();
			
			// If still no page saved
			if (page == ApplicationConstants.NO_PAGE_SAVED) {
				page = ApplicationConstants.PAGES_FIRST;
			}
		} 
		
		Log.d(TAG, "page: "+ page+" fyi: "+QuranSettings.getInstance().getLastPage());
		
		return page;
	}
	
	
	
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putInt("lastPage", QuranSettings.getInstance().getLastPage());
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onResume() {
		super.onResume();
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
	
	protected void updatePageInfo(int position){
		Log.d("QuranAndroid", "Update page info: " + position);
		titleText.setText(ArabicStyle.reshape(QuranInfo.getPageTitle(position)));
		seekBar.setProgress(ApplicationConstants.PAGES_LAST - position);
	}
	
	private void updatePageInfo() {
		updatePageInfo(quranPageFeeder.getCurrentPagePosition());
		QuranSettings.getInstance().setLastPage(quranPageFeeder.getCurrentPagePosition());
	}
	
	protected void toggleMode(){
		Log.d("exp_v", "in toggle mode");
		if (inReadingMode){
	        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        
	        seekBar.setVisibility(TextView.VISIBLE);
	        titleText.setVisibility(TextView.VISIBLE);
	        btnBookmark.setVisibility(View.VISIBLE);
			adjustBookmarkView();
			updatePageInfo();
		}
		else {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
	        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
	        
	        seekBar.setVisibility(TextView.INVISIBLE);
	        titleText.setVisibility(TextView.INVISIBLE);
	        btnBookmark.setVisibility(View.INVISIBLE);
		}
		
		inReadingMode = !inReadingMode;
	}
}
