package com.quran.labs.androidquran.widgets;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.widget.ImageView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.util.QuranFileUtils;

public class HighlightingImageView extends ImageView {
	private List<AyahBounds> currentlyHighlighting = null;
	private boolean colorFilterOn = false;
	private String highightedAyah = null;
   private Bitmap mHighlightBitmap = null;
	
	public HighlightingImageView(Context context){
		super(context);
      init(context);
	}
	
	public HighlightingImageView(Context context, AttributeSet attrs){
		super(context, attrs);
      init(context);
	}
	
	public HighlightingImageView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
      init(context);
	}

   public void init(Context context){
      mHighlightBitmap = BitmapFactory.decodeResource(
              getResources(), R.drawable.highlight);
   }

	public void unhighlight(){
		this.currentlyHighlighting = null;
		this.invalidate();
	}
	
	public void toggleHighlight(int sura, int ayah) {
		if (highightedAyah != null && highightedAyah.equals(sura + ":" + ayah)) {
			currentlyHighlighting = null;
			highightedAyah = null;
		} else {
			highlightAyah(sura, ayah);
			highightedAyah = sura + ":" + ayah;
		} 
	}
	
	public void highlightAyah(int sura, int ayah){
		try {
			String filename = QuranFileUtils.getAyaPositionFileName();
			if (filename == null) return;
			
			AyahInfoDatabaseHandler handler =
				new AyahInfoDatabaseHandler(filename);
			Cursor cursor = handler.getVerseBounds(sura, ayah);
         SparseArray<AyahBounds> lineCoords = new SparseArray<AyahBounds>();
			AyahBounds first = null, last = null, current = null;
			if ((cursor == null) || (!cursor.moveToFirst()))
				return;
			do {
				current = new AyahBounds(cursor.getInt(1), cursor.getInt(4),
						cursor.getInt(5), cursor.getInt(6), cursor.getInt(7),
						cursor.getInt(8));
				if (first == null) first = current;
				if (lineCoords.get(current.getLine()) == null){
					lineCoords.put(current.getLine(), current);
            }
				else { lineCoords.get(current.getLine()).engulf(current); }
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
			AyahBounds last, SparseArray<AyahBounds> lineCoordinates){
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

   @Override
   public void setImageBitmap(Bitmap bitmap){
      super.setImageBitmap(bitmap);
      adjustNightMode();
   }

	public void adjustNightMode() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
		boolean nightMode = prefs.getBoolean(Constants.PREF_NIGHT_MODE, false);
		if (nightMode && !colorFilterOn) {
			setBackgroundColor(Color.BLACK);
			float[] matrix = { 
				-1, 0, 0, 0, 255,
				0, -1, 0, 0, 255,
				0, 0, -1, 0, 255,
				0, 0, 0, 1, 0 
			};
			setColorFilter(new ColorMatrixColorFilter(matrix));
			colorFilterOn = true;
		} else if (!nightMode && colorFilterOn) {
			clearColorFilter();
			setBackgroundColor(getResources().getColor(R.color.page_background));
			colorFilterOn = false;
		}
		invalidate();
   }
	
   private class PageScalingData {
		float screenRatio, pageRatio, scaledPageHeight, scaledPageWidth, 
				widthFactor, heightFactor, offsetX, offsetY;
		
		public PageScalingData(Drawable page) {
		   screenRatio = (1.0f*getHeight())/(1.0f*getWidth());
			pageRatio = (float) (1.0* page.getIntrinsicHeight()/page.getIntrinsicWidth());
			// depending on whether or not you will have a top or bottom offset
			if (screenRatio < pageRatio){
				scaledPageHeight = getHeight();
				scaledPageWidth = (float) (1.0*getHeight()/page.getIntrinsicHeight()*page.getIntrinsicWidth());
			} else {
				scaledPageWidth = getWidth();
				scaledPageHeight = (float)(1.0*getWidth()/page.getIntrinsicWidth()*page.getIntrinsicHeight());
			}
			
			widthFactor = scaledPageWidth / page.getIntrinsicWidth();
			heightFactor = scaledPageHeight / page.getIntrinsicHeight();
			
			offsetX = (getWidth() - scaledPageWidth)/2;
			offsetY = (getHeight() - scaledPageHeight)/2;
		}
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (this.currentlyHighlighting != null){
			Drawable page = this.getDrawable();
			if (page != null){
				PageScalingData scalingData = new PageScalingData(page);

				for (AyahBounds b : currentlyHighlighting){
					RectF scaled = new RectF(b.getMinX() * scalingData.widthFactor,
							b.getMinY() * scalingData.heightFactor,
                     b.getMaxX() * scalingData.widthFactor,
							b.getMaxY() * scalingData.heightFactor);
					scaled.offset(scalingData.offsetX, scalingData.offsetY);

               // work around a 4.0.2 bug where src as null throws npe
               // http://code.google.com/p/android/issues/detail?id=24830
               Rect src = new Rect(0, 0, mHighlightBitmap.getWidth(),
                       mHighlightBitmap.getHeight());
					canvas.drawBitmap(mHighlightBitmap, src, scaled, null);
				}
			}
		}
	}
	
	public float[] getPageXY(float screenX, float screenY) {
		Drawable page = this.getDrawable();
		if (page == null)
			return null;
		PageScalingData scalingData = new PageScalingData(page);
		float pageX = screenX / scalingData.widthFactor - scalingData.offsetX;
		float pageY = screenY / scalingData.heightFactor - scalingData.offsetY;
		float[] pageXY = {pageX, pageY};
		return pageXY;
	}
}