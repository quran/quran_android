package com.quran.labs.androidquran.ui.helpers;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.util.BitmapUtils;

public class PageRetriever extends Thread {
	private int mPage;
	private View mView;
	private Context mContext;
	
	private static String TAG = "PageRetriever";
	
	public PageRetriever(Context context, View view, int page){
		mPage = page;
		mView =  view;
		mContext = context;
		
		updateViewForUser(mView, true, false);
	}
	
	@Override
	public void run() {
		Bitmap bitmap = BitmapUtils.getBitmap(mPage);
		((Activity)mContext).runOnUiThread(
				new PageDisplayer(bitmap, mView, mPage));
		
		//clear for GC
		mView = null;
	}
	
	class PageDisplayer implements Runnable {
		private Bitmap mBitmap;
		private View mView;
		private int mPage;
		
		public PageDisplayer(Bitmap bitmap, View view, int page){
			mBitmap = bitmap;
			mView = view;
			mPage = page;
		}
		
		public void run(){
			ImageView iv = (ImageView)mView.findViewById(R.id.page_image);
			if (mBitmap == null) {
	        	Log.d(TAG, "Page not found: " + mPage);
	        	updateViewForUser(mView, false, true);
	        } else {
	        	iv.setImageBitmap(mBitmap);
	        	updateViewForUser(mView, false, false);
	        }
			
			//clear for GC
			iv = null;
			mBitmap = null;
			mView = null;
		}
	}
	
	protected void updateViewForUser(View v, boolean loading,
			boolean pageNotFound){
		TextView tv = (TextView)v.findViewById(R.id.txtPageNotFound);
		ImageView iv = (ImageView)v.findViewById(R.id.page_image);
		if (loading || pageNotFound){
			tv.setText(loading? R.string.pageLoading : R.string.pageNotFound);
			tv.setVisibility(View.VISIBLE);
			iv.setVisibility(View.GONE);
		}
		else {
			tv.setVisibility(View.GONE);
			iv.setVisibility(View.VISIBLE);
		}
	}
}
