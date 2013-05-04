package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.graphics.*;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HighlightingImageView extends ImageView {
   // Max/Min font sizes for text overlay
   private static final float MAX_FONT_SIZE = 28.0f;
   private static final float MIN_FONT_SIZE = 16.0f;
   
	private List<AyahBounds> mCurrentlyHighlighting = null;
	private boolean mColorFilterOn = false;
	private String mHighightedAyah = null;
   private Bitmap mHighlightBitmap = null;
   private boolean mIsNightMode = false;
   private int mNightModeTextBrightness = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
   
   // Params for drawing text
   private OverlayParams mOverlayParams = null;
   private Rect mPageBounds = null;
   private boolean mDidDraw = false;
   private Map<String, List<AyahBounds>> mCoordinatesData;
	
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
		mCurrentlyHighlighting = null;
		invalidate();
	}

   public void setCoordinateData(Map<String, List<AyahBounds>> data){
      mCoordinatesData = data;
   }

   public void setNightMode(boolean isNightMode){
      mIsNightMode = isNightMode;
   }
	
   public void setNightModeTextBrightness(int nightModeTextBrightness){
      mNightModeTextBrightness = nightModeTextBrightness;
   }
   
	public void highlightAyah(int sura, int ayah){
      String key = sura + ":" + ayah;
      if (mCoordinatesData != null){
         List<AyahBounds> bounds = mCoordinatesData.get(key);
         if (bounds != null){
            doHighlightAyah(bounds);
         }
      }
	}
	
	private void doHighlightAyah(List<AyahBounds> lineCoordinates){
      if (lineCoordinates == null || lineCoordinates.size() == 0){
         return;
      }

      ArrayList<AyahBounds> rangesToDraw = new ArrayList<AyahBounds>();
      for (AyahBounds bound : lineCoordinates){
         rangesToDraw.add(bound);
      }
		mCurrentlyHighlighting = rangesToDraw;
	}
	
	public AyahBounds getYBoundsForCurrentHighlight() {
		if (mCurrentlyHighlighting == null){
			return null;
      }

		Integer upperBound = null;
		Integer lowerBound = null;
		for (AyahBounds bounds : mCurrentlyHighlighting) {
			if (upperBound == null || bounds.getMinY() < upperBound){
				upperBound = bounds.getMinY();
         }

			if (lowerBound == null || bounds.getMaxY() > lowerBound){
				lowerBound = bounds.getMaxY();
         }
		}
		AyahBounds yBounds = null;
		if (upperBound != null && lowerBound != null){
			yBounds = new AyahBounds(0, 0, 0, upperBound, 0, lowerBound);
      }
		return yBounds;
	}

   @Override
   public void setImageBitmap(Bitmap bitmap){
      super.setImageBitmap(bitmap);
      adjustNightMode();
   }

	public void adjustNightMode() {
		if (mIsNightMode && !mColorFilterOn) {
			float[] matrix = { 
				-1, 0, 0, 0, mNightModeTextBrightness,
				0, -1, 0, 0, mNightModeTextBrightness,
				0, 0, -1, 0, mNightModeTextBrightness,
				0, 0, 0, 1, 0 
			};
			setColorFilter(new ColorMatrixColorFilter(matrix));
			mColorFilterOn = true;
		} else if (!mIsNightMode && mColorFilterOn) {
			clearColorFilter();
			mColorFilterOn = false;
		}
		invalidate();
   }
	
   private class PageScalingData {
		float screenRatio, pageRatio, scaledPageHeight, scaledPageWidth,
				widthFactor, heightFactor, offsetX, offsetY;
		
		public PageScalingData(Drawable page) {
		   screenRatio = (1.0f*getHeight())/(1.0f*getWidth());
			pageRatio = (float) (1.0* page.getIntrinsicHeight() /
                 page.getIntrinsicWidth());
			// depending on whether or not you will have a top or bottom offset
			if (screenRatio < pageRatio){
				scaledPageHeight = getHeight();
				scaledPageWidth = (float) (1.0*getHeight() /
                    page.getIntrinsicHeight()*page.getIntrinsicWidth());
			}
         else {
				scaledPageWidth = getWidth();
				scaledPageHeight = (float)(1.0*getWidth() /
                    page.getIntrinsicWidth()*page.getIntrinsicHeight());
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
      if (mPageBounds == null){ return; }

      mOverlayParams = new OverlayParams();
      mOverlayParams.suraText = QuranInfo.getSuraNameFromPage(
              getContext(), page, true);
      mOverlayParams.juzText = QuranInfo.getJuzString(getContext(), page);
      mOverlayParams.pageText = QuranUtils.getLocalizedNumber(
              getContext(), page);
      mOverlayParams.showOverlay = show;

      if (show && !mDidDraw){
         invalidate();
      }
   }

   public void setPageBounds(Rect rect){
      mPageBounds = rect;
   }
   
   private boolean initOverlayParams() {
      if (mOverlayParams == null || mPageBounds == null){ return false; }
      
      // Overlay params previously initiated; skip
      if (mOverlayParams.init){ return true; }
      
      Drawable page = this.getDrawable();
      if (page == null){ return false; }
      PageScalingData scalingData = new PageScalingData(page);
      
      mOverlayParams.paint = new Paint(Paint.ANTI_ALIAS_FLAG
                                     | Paint.DEV_KERN_TEXT_FLAG);
      mOverlayParams.paint.setTextSize(MAX_FONT_SIZE);
      int overlayColor = getResources().getColor(R.color.overlay_text_color);
      if (mIsNightMode) {
         overlayColor = Color.rgb(mNightModeTextBrightness,
               mNightModeTextBrightness, mNightModeTextBrightness);
      }
      mOverlayParams.paint.setColor(overlayColor);
      
      // Use font metrics to calculate the maximum possible height of the text
      FontMetrics fm = mOverlayParams.paint.getFontMetrics();
      float textHeight = fm.bottom-fm.top;
      
      // Text size scale based on the available 'header' and 'footer' space
      // (i.e. gap between top/bottom of screen and actual start of the
      // 'bitmap')
      float scale = scalingData.offsetY/textHeight;
      
      // If the height of the drawn text might be greater than the available
      // gap... scale down the text size by the calculated scale
      if (scale < 1.0) {
         // If after scaling the text size will be less than the minimum
         // size... get page bounds from db and find the empty area within
         // the image and utilize that as well.
         if (MAX_FONT_SIZE * scale < MIN_FONT_SIZE) {
            float emptyYTop = scalingData.offsetY +
                    mPageBounds.top * scalingData.heightFactor;
            float emptyYBottom = scalingData.offsetY
                  + (scalingData.scaledPageHeight -
                    mPageBounds.bottom*scalingData.heightFactor);
            float emptyY = Math.min(emptyYTop, emptyYBottom);
            scale = Math.min(emptyY/textHeight, 1.0f);
         }
         // Set the scaled text size, and update the metrics
         mOverlayParams.paint.setTextSize(MAX_FONT_SIZE*scale);
         fm = mOverlayParams.paint.getFontMetrics();
      }
      
      // Calculate where the text's baseline should be
      // (for top text and bottom text)
      // (p.s. parts of the glyphs will be below the baseline such as a
      // 'y' or 'ي')
      mOverlayParams.topBaseline = -fm.top;
      mOverlayParams.bottomBaseline = getHeight()-fm.bottom;
      
      // Calculate the horizontal margins off the edge of screen
      mOverlayParams.offsetX = scalingData.offsetX
            + (getWidth() - mPageBounds.width()*scalingData.widthFactor)/2.0f;
      
      mOverlayParams.init = true;
      return true;
   }
   
   private void overlayText(Canvas canvas) {
      if (mOverlayParams == null || !initOverlayParams()) return;
      
      mOverlayParams.paint.setTextAlign(Align.LEFT);
      canvas.drawText(mOverlayParams.suraText,
            mOverlayParams.offsetX, mOverlayParams.topBaseline,
              mOverlayParams.paint);
      mOverlayParams.paint.setTextAlign(Align.RIGHT);
      canvas.drawText(mOverlayParams.juzText,
            getWidth()- mOverlayParams.offsetX, mOverlayParams.topBaseline,
              mOverlayParams.paint);
      mOverlayParams.paint.setTextAlign(Align.CENTER);
      canvas.drawText(mOverlayParams.pageText,
            getWidth()/2.0f, mOverlayParams.bottomBaseline,
              mOverlayParams.paint);
      mDidDraw = true;
   }
   
	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
      mDidDraw = false;
		if (mOverlayParams != null && mOverlayParams.showOverlay) {
			try { overlayText(canvas); }
			catch (Exception e) {}
		}

		if (this.mCurrentlyHighlighting != null){
			Drawable page = this.getDrawable();
			if (page != null){
				PageScalingData scalingData = new PageScalingData(page);

				for (AyahBounds b : mCurrentlyHighlighting){
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
