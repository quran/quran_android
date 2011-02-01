package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Gallery;

public class QuranGallery extends Gallery {

	public QuranGallery(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
		int vX;
		if (e2.getX() > e1.getX())
			vX = 10;
		else vX = -10;
		return super.onFling(e1, e2, vX, velocityY);
	}
}
