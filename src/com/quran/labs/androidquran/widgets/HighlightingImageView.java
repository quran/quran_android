package com.quran.labs.androidquran.widgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

public class HighlightingImageView extends ImageView {
	private List<AyahBounds> currentlyHighlighting = null;
	private boolean colorFilterOn = false;
	
	public HighlightingImageView(Context context){
		super(context);
	}
	
	public HighlightingImageView(Context context, AttributeSet attrs){
		super(context, attrs);
	}
	
	public HighlightingImageView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
	}

	public void unhighlight(){
		this.currentlyHighlighting = null;
		this.invalidate();
	}
	
	public void highlightAyah(int sura, int ayah){
		try {
			String filename = QuranUtils.getAyaPositionFileName();
			if (filename == null) return;
			
			AyahInfoDatabaseHandler handler =
				new AyahInfoDatabaseHandler(filename);
			Cursor cursor = handler.getVerseBounds(sura, ayah);
			Map<Integer, AyahBounds> lineCoords =
				new HashMap<Integer, AyahBounds>();
			AyahBounds first = null, last = null, current = null;
			if ((cursor == null) || (!cursor.moveToFirst()))
				return;
			do {
				current = new AyahBounds(cursor.getInt(1), cursor.getInt(4),
						cursor.getInt(5), cursor.getInt(6), cursor.getInt(7),
						cursor.getInt(8));
				if (first == null) first = current;
				if (!lineCoords.containsKey(current.getLine()))
					lineCoords.put(current.getLine(), current);
				else lineCoords.get(current.getLine()).engulf(current);
			} while (cursor.moveToNext());
			
			if ((first != null) && (current != null) &&
				(first.getPosition() != current.getPosition()))
				last = current;
			
			cursor.close();
			handler.closeDatabase();
			doHighlightAyah(first, last, lineCoords);
		}
		catch (SQLException se){
		}
	}
	
	private void doHighlightAyah(AyahBounds first,
			AyahBounds last, Map<Integer, AyahBounds> lineCoordinates){
		if (first == null) return;
		ArrayList<AyahBounds> rangesToDraw = new ArrayList<AyahBounds>();
		if (last == null)
			rangesToDraw.add(first);
		else {
			if (first.getLine() == last.getLine()){
				first.engulf(last);
				rangesToDraw.add(first);
			}
			else {
				AyahBounds b = lineCoordinates.get(first.getLine());
				rangesToDraw.add(b);
				
				int currentLine = first.getLine() + 1;
				int diff = last.getLine() - first.getLine() - 1;
				for (int i = 0; i < diff; i++){
					b = lineCoordinates.get(currentLine + i);
					rangesToDraw.add(b);
				}
				
				b = lineCoordinates.get(last.getLine());
				rangesToDraw.add(b);
			}
		}
		
		/*
		for (AyahBounds b : rangesToDraw){
			android.util.Log.d("ranges", "got: " + b.getMinX() + ", " + b.getMinY() +
					", " + b.getMaxX() + ", " + b.getMaxY());
		}
		*/
		
		this.currentlyHighlighting = rangesToDraw;
	}
	
	public AyahBounds getYBoundsForCurrentHighlight() {
		if (currentlyHighlighting == null)
			return null;
		Integer upperBound = null;
		Integer lowerBound = null;
		for (AyahBounds bounds : currentlyHighlighting) {
			if (upperBound == null || bounds.getMinY() < upperBound)
				upperBound = bounds.getMinY();
			if (lowerBound == null || bounds.getMaxY() > lowerBound)
				lowerBound = bounds.getMaxY();
		}
		AyahBounds yBounds = null;
		if (upperBound != null && lowerBound != null)
			yBounds = new AyahBounds(0, 0, 0, upperBound, 0, lowerBound);
		return yBounds;
	}
	
	public void adjustNightMode() {
		if (QuranSettings.getInstance().isNightMode() && !colorFilterOn) {
			setBackgroundColor(Color.BLACK);
			float[] matrix = { 
				-1, 0, 0, 0, 255,
				0, -1, 0, 0, 255,
				0, 0, -1, 0, 255,
				0, 0, 0, 1, 0 
			};
			setColorFilter(new ColorMatrixColorFilter(matrix));
			colorFilterOn = true;
		} else if (!QuranSettings.getInstance().isNightMode() && colorFilterOn) {
			clearColorFilter();
			setBackgroundColor(getResources().getColor(R.color.page_background));
			colorFilterOn = false;
		}
		invalidate();
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (this.currentlyHighlighting != null){
			Drawable page = this.getDrawable();
			if (page != null){
				Bitmap bm = BitmapFactory.decodeResource(
						getResources(), R.drawable.highlight);
				
				float screenRatio = QuranScreenInfo.getInstance().getRatio();
				float pageRatio = (float) (1.0* page.getIntrinsicHeight()/page.getIntrinsicWidth());
				
				float scaledPageHeight;
				float scaledPageWidth;
				
				// depending on whether or not you will have a top or bottom offset
				if (screenRatio < pageRatio){
					scaledPageHeight = getHeight();
					scaledPageWidth = (float) (1.0*getHeight()/page.getIntrinsicHeight()*page.getIntrinsicWidth());
				} else {
					scaledPageWidth = getWidth();
					scaledPageHeight = (float)(1.0*getWidth()/page.getIntrinsicWidth()*page.getIntrinsicHeight());
				}
				
				float widthFactor = scaledPageWidth / page.getIntrinsicWidth();
				float heightFactor = scaledPageHeight / page.getIntrinsicHeight();
				
				float offsetX = (getWidth() - scaledPageWidth)/2;
				float offsetY = (getHeight() - scaledPageHeight)/2;
			
				for (AyahBounds b : currentlyHighlighting){
					RectF scaled = new RectF(b.getMinX() * widthFactor,
							b.getMinY() * heightFactor, b.getMaxX() * widthFactor,
							b.getMaxY() * heightFactor);
					scaled.offset(offsetX, offsetY);
					canvas.drawBitmap(bm, null, scaled, null);
				}
			}
		}
	}
}
