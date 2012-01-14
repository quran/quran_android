package com.quran.labs.androidquran.ui.helpers;

import android.app.Activity;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import com.quran.labs.androidquran.R;

public class QuranPageAdapter extends PagerAdapter {

	private Context mContext = null;
	private LayoutInflater mInflater = null;
	private PaintDrawable mLeftGradient, mRightGradient = null;

	public QuranPageAdapter(Context context){
		mContext = context;
		mInflater = (LayoutInflater)context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE);

		int width = ((Activity)context).getWindowManager()
				.getDefaultDisplay().getWidth();
		mLeftGradient = getPaintDrawable(width, 0);
		mRightGradient = getPaintDrawable(0, width);
	}

	private PaintDrawable getPaintDrawable(int startX, int endX){
		PaintDrawable drawable = new PaintDrawable();
		drawable.setShape(new RectShape());
		drawable.setShaderFactory(getShaderFactory(startX, endX));
		return drawable;
	}

	private ShapeDrawable.ShaderFactory
	getShaderFactory(final int startX, final int endX){
		return new ShapeDrawable.ShaderFactory(){

			@Override
			public Shader resize(int width, int height) {
				return new LinearGradient(startX, 0, endX, 0,
						new int[]{ 0xFFDCDAD5, 0xFFFDFDF4,
						0xFFFFFFFF, 0xFFFDFBEF },
						new float[]{ 0, 0.18f, 0.48f, 1 },
						Shader.TileMode.REPEAT);
			}
		};
	}

	@Override
	public int getCount(){ return 604; }

	@Override
	public Object instantiateItem(View collection, int position){
		android.util.Log.d("PagerActivity", "instantiate " + position +
				", page " + (604 - position));

		int page = 604 - position;
		View view = mInflater.inflate(R.layout.quran_page_layout, null);
		view.setBackgroundDrawable((page % 2 == 0? mLeftGradient : mRightGradient));

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
		
		new PageRetriever(mContext, view, page).start();
		
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
