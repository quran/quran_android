package com.quran.labs.androidquran;

import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import com.quran.labs.androidquran.common.PageViewQuranActivity;
import com.quran.labs.androidquran.common.QuranPageFeeder;
import com.quran.labs.androidquran.util.BookmarksManager;
import com.quran.labs.androidquran.util.QuranSettings;

public class QuranViewActivity extends PageViewQuranActivity {
	private static final String TAG = "QuranViewActivity";
	
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
}
