package com.quran.labs.androidquran.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.Window;
import android.view.WindowManager;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.helpers.QuranPageAdapter;

public class PagerActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
    			WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
		setContentView(R.layout.quran_page_activity);
		android.util.Log.d("PagerActivity", "onCreate()");
		int page = 100;
		Intent intent = getIntent();
		Bundle extras = intent.getExtras();
		if (extras != null)
			page = 604 - extras.getInt("page");
		
		QuranPageAdapter adapter = new QuranPageAdapter(this);
		ViewPager pager = (ViewPager)findViewById(R.id.quran_pager);
		pager.setAdapter(adapter);
		pager.setCurrentItem(page);
	}
}
