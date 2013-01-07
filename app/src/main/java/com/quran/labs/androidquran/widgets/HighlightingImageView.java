package com.quran.labs.androidquran.widgets;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.widget.ImageView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranFileUtils;

public class HighlightingImageView extends ImageView {
   // Max/Min font sizes for text overlay
   private static final float MAX_FONT_SIZE = 28.0f;
   private static final float MIN_FONT_SIZE = 16.0f;
   
	private List<AyahBounds> currentlyHighlighting = null;
	private boolean colorFilterOn = false;
	private String highightedAyah = null;
   private Bitmap mHighlightBitmap = null;
   
   // Params for drawing text
   private OverlayParams overlayParams = null;
   private Rect pageBounds = null;
	
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
	
   private class OverlayParams {
      boolean init = false;
      boolean showOverlay = false;
      Paint paint = null;
      float offsetX;
      float topBaseline;
      float bottomBaseline;
      String suraText = null;
      String juzText = null;
      String pageText = null;
   }
   
   public void setOverlayText(int page, boolean show) {
      // Calculate page bounding rect from ayainfo db
      this.pageBounds = getPageBounds(page);
      if (pageBounds == null) return;
      
      overlayParams = new OverlayParams();
      overlayParams.suraText = QuranInfo.getSuraNameFromPage(getContext(), page, true);
      overlayParams.juzText = QuranInfo.getJuzString(getContext(), page);
      overlayParams.pageText = String.format("%1$d", page);
      overlayParams.showOverlay = show;
   }
   
   private Rect getPageBounds(int page){
      Rect r = null;
      try {
         String filename = QuranFileUtils.getAyaPositionFileName();
         if (filename == null) return null;
         AyahInfoDatabaseHandler handler =
               new AyahInfoDatabaseHandler(filename);
         r = handler.getPageBounds(page);
         handler.closeDatabase();
      }
      catch (SQLException se){/*do nothing*/}
      return r;
   }
   
   private boolean initOverlayParams() {
      if (overlayParams == null || pageBounds == null) return false;
      
      // Overlay params previously initiated; skip
      if (overlayParams.init) return true;
      
      Drawable page = this.getDrawable();
      if (page == null) return false;
      PageScalingData scalingData = new PageScalingData(page);
      
      overlayParams.paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
      overlayParams.paint.setTextSize(MAX_FONT_SIZE);
      overlayParams.paint.setColor(getResources().getColor(R.color.overlay_text_color));
      
      // Use font metrics to calculate the maximum possible height of the text
      FontMetrics fm = overlayParams.paint.getFontMetrics();
      float textHeight = fm.bottom-fm.top;
      
      // Text size scale based on the available 'header' and 'footer' space
      // (i.e. gap between top/bottom of screen and actual start of the 'bitmap')
      float scale = scalingData.offsetY/textHeight;
      
      // If the height of the drawn text might be greater than the available gap...
      // scale down the text size by the calculated scale
      if (scale < 1.0) {
         // If after scaling the text size will be less than the minimum size...
         // get page bounds from db and find the empty area within the image and utilize that as well.
         if (MAX_FONT_SIZE*scale < MIN_FONT_SIZE) {
            float emptyYTop = scalingData.offsetY + pageBounds.top*scalingData.heightFactor;
            float emptyYBottom = scalingData.offsetY
                  + (scalingData.scaledPageHeight - pageBounds.bottom*scalingData.heightFactor);
            float emptyY = Math.min(emptyYTop, emptyYBottom);
            scale = Math.min(emptyY/textHeight, 1.0f);
         }
         // Set the scaled text size, and update the metrics
         overlayParams.paint.setTextSize(MAX_FONT_SIZE*scale);
         fm = overlayParams.paint.getFontMetrics();
         textHeight = fm.bottom-fm.top;
      }
      
      // Calculate where the text's baseline should be (for top text and bottom text)
      // (p.s. parts of the glyphs will be below the baseline such as a 'y' or 'ي')
      overlayParams.topBaseline = -fm.top;
      overlayParams.bottomBaseline = getHeight()-fm.bottom;
      
      // Calculate the horizontal margins off the edge of screen
      overlayParams.offsetX = scalingData.offsetX
            + (getWidth() - pageBounds.width()*scalingData.widthFactor)/2.0f;
      
      overlayParams.init = true;
      return true;
   }
   
   private void overlayText(Canvas canvas) {
      if (overlayParams == null || !initOverlayParams()) return;
      
      overlayParams.paint.setTextAlign(Align.LEFT);
      canvas.drawText(overlayParams.suraText,
            overlayParams.offsetX, overlayParams.topBaseline, overlayParams.paint);
      overlayParams.paint.setTextAlign(Align.RIGHT);
      canvas.drawText(overlayParams.juzText,
            getWidth()-overlayParams.offsetX, overlayParams.topBaseline, overlayParams.paint);
      overlayParams.paint.setTextAlign(Align.CENTER);
      canvas.drawText(overlayParams.pageText,
            getWidth()/2.0f, overlayParams.bottomBaseline, overlayParams.paint);
   }
   
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (overlayParams != null && overlayParams.showOverlay) {
			try {overlayText(canvas);}
			catch (Exception e) {/*do nothing*/} // Temporary to avoid any unanticipated FC's
		}
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
