package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ScrollView;

public class GalleryFriendlyScrollView extends ScrollView {
	public GalleryFriendlyScrollView(Context context) {
		super(context);
	}
	
	public GalleryFriendlyScrollView(Context context, AttributeSet attrs){
		super(context, attrs);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		return super.onTouchEvent(ev);
	}

}
