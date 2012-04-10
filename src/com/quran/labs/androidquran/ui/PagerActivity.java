package com.quran.labs.androidquran.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;
import com.quran.labs.androidquran.util.ArabicStyle;
import com.quran.labs.androidquran.util.QuranSettings;

public class PagerActivity extends Activity {

	private static String TAG = "PagerActivity";
	private SharedPreferences prefs = null;
	private long lastPopupTime = 0;
	private ViewPager pager;

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
    			WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        prefs = PreferenceManager.getDefaultSharedPreferences(
        		getApplicationContext());
        
		setContentView(R.layout.quran_page_activity);
		android.util.Log.d("PagerActivity", "onCreate()");
		int page = 100;
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null)
			page = 604 - extras.getInt("page");
		
		lastPopupTime = System.currentTimeMillis();
		QuranPageAdapter adapter = new QuranPageAdapter(this);
		pager = (ViewPager)findViewById(R.id.quran_pager);
		pager.setAdapter(adapter);
		pager.setOnPageChangeListener(new OnPageChangeListener(){

			@Override
			public void onPageScrollStateChanged(int state) {}

			@Override
			public void onPageScrolled(int position, float positionOffset,
					int positionOffsetPixels) {
			}

			@Override
			public void onPageSelected(int position) {
				android.util.Log.d(TAG, "onPageSelected(): " + position);
				int page = 604 - position;
				QuranSettings.getInstance().setLastPage(page);
				QuranSettings.save(prefs);
				if (QuranSettings.getInstance().isDisplayMarkerPopup())
					displayMarkerPopup(page);
			}
			
		});
		
		pager.setCurrentItem(page);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "keep screen on - reading started");
		pager.setKeepScreenOn(true);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		Log.d(TAG, "keep screen off - reading finished");
		pager.setKeepScreenOn(false);
	}
	
	public void displayMarkerPopup(int page) {
		if(System.currentTimeMillis() - lastPopupTime < 3000)
			return;
		int rub3 = QuranInfo.getRub3FromPage(page);
		if (rub3 == -1)
			return;
		int hizb = (rub3 / 4) + 1;
		StringBuilder sb = new StringBuilder();
		
		if (rub3 % 8 == 0) {
			sb.append(getString(R.string.quran_juz2)).append(' ').append((hizb/2) + 1);
		} else {
			int remainder = rub3 % 4;
			if (remainder == 1)
				sb.append(getString(R.string.quran_rob3)).append(' ');
			else if (remainder == 2)
				sb.append(getString(R.string.quran_nos)).append(' ');
			else if (remainder == 3)
				sb.append(getString(R.string.quran_talt_arb3)).append(' ');
			sb.append(getString(R.string.quran_hizb)).append(' ').append(hizb);
		}
		Toast.makeText(this, ArabicStyle.reshape(sb.toString()), Toast.LENGTH_SHORT).show();
		lastPopupTime = System.currentTimeMillis();
	}
}