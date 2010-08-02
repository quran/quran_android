package com.quran.labs.androidquran;

import com.quran.labs.androidquran.util.QuranScreenInfo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.ImageView;

public class QuranImageView extends ImageView {
	
	public QuranImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public QuranImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public QuranImageView(Context context) {
		super(context);
	}
	
	protected void onDraw(Canvas canvas) {	
		super.onDraw(canvas);
		
		QuranScreenInfo qsi = QuranScreenInfo.getInstance();
        Paint p = new Paint();
        p.setColor(Color.parseColor("#802A2A"));
        int length = qsi.getHeight();
        int width = qsi.getWidth();
        canvas.drawLine(0, 0, 0, length, p);
        canvas.drawLine(width, 0, width, length, p);

        invalidate();
	}
}
