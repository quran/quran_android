package com.quran.labs.androidquran;

import java.text.NumberFormat;

import com.quran.labs.androidquran.R;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.ImageView;

public class QuranView extends Activity {

	private int page;
	
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
		
		showSura();
	}
	
	private void showSura(){
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMinimumIntegerDigits(3);
		String filename = "page" + nf.format(page) + ".png";
		
		Drawable drawable = QuranUtils.getImageFromSD(filename);
		if (drawable == null)
			drawable = QuranUtils.getImageFromWeb(filename);
		if (drawable == null){
			Resources res = this.getResources();
			drawable = res.getDrawable(R.drawable.page001);
		}
		
		ImageView imageView = (ImageView)findViewById(R.id.pageview);
		imageView.setImageDrawable(drawable);
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
