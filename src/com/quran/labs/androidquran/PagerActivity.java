package com.quran.labs.androidquran;

import java.text.NumberFormat;
import java.util.Locale;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import com.quran.labs.androidquran.util.QuranUtils;

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
		
		QuranAdapter adapter = new QuranAdapter(this);
		ViewPager pager = (ViewPager)findViewById(R.id.quran_pager);
		pager.setAdapter(adapter);
		pager.setCurrentItem(page);
	}
	
	class QuranAdapter extends PagerAdapter {
		private LayoutInflater inflater = null;
		
		public QuranAdapter(Context context){
			inflater = (LayoutInflater)context.getSystemService(
					Context.LAYOUT_INFLATER_SERVICE);
		}
		
		public int getCount(){ return 604; }
		
		public Object instantiateItem(View collection, int position){
			android.util.Log.d("PagerActivity", "instantiate " + position);
			
			int page = 604 - position;
			View view = inflater.inflate(R.layout.quran_page_layout, null);
			ImageView iv = (ImageView)view.findViewById(R.id.page_image);
			
			/*
			int gradient = R.drawable.right_gr;
			if (page % 2 == 0) gradient = R.drawable.left_gr;
			iv.setBackgroundResource(gradient);
			*/
			
			int startX = 0;
			int endX = getWindowManager().getDefaultDisplay().getWidth();
			if (page % 2 == 0){
				startX = endX;
				endX = 0;
			}
			final int sX = startX;
			final int eX = endX;
			
			ShapeDrawable.ShaderFactory sf = new ShapeDrawable.ShaderFactory(){
				
				@Override
				public Shader resize(int width, int height) {
					return new LinearGradient(sX, 0, eX, 0,
							new int[]{ 0xFFDCDAD5, 0xFFFDFDF4,
									   0xFFFFFFFF, 0xFFFDFBEF },
							new float[]{ 0, 0.18f, 0.48f, 1 },
							Shader.TileMode.REPEAT);
				}
			};
			PaintDrawable p = new PaintDrawable();
			p.setShape(new RectShape());
			p.setShaderFactory(sf);
			iv.setBackgroundDrawable(p);
			
			ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
			ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
			if (page % 2 == 0){
				rightBorder.setVisibility(View.GONE);
				leftBorder.setBackgroundResource(R.drawable.border_left);
			}
			else {
				rightBorder.setVisibility(View.VISIBLE);
				rightBorder.setBackgroundResource(R.drawable.border_right);
				leftBorder.setBackgroundResource(R.drawable.dark_line);
			}

			NumberFormat nf = NumberFormat.getInstance(Locale.US);
			nf.setMinimumIntegerDigits(3);
			String filename = "page" + nf.format(page) + ".png";
			
			Bitmap image = QuranUtils.getImageFromSD(filename);
			iv.setImageBitmap(image);
			((ViewPager)collection).addView(view, 0);
			return view;
		}
		
		@Override
		public void destroyItem(View collection, int position, Object view){
			((ViewPager)collection).removeView((View)view);
		}
		
		@Override
		public boolean isViewFromObject(View view, Object object){
			return view.equals(object);
		}
	}
}
