package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranUtils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.SparseArray;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class HighlightingImageView extends RecyclingImageView {

  // Max/Min font sizes for text overlay
  private static final float MAX_FONT_SIZE = 28.0f;
  private static final float MIN_FONT_SIZE = 16.0f;

  // Sorted map so we use highest priority highlighting when iterating
  private SortedMap<HighlightType, Set<String>> mCurrentHighlights =
      new TreeMap<HighlightType, Set<String>>();
  private boolean mColorFilterOn = false;
  private boolean mIsNightMode = false;
  private int mNightModeTextBrightness =
      Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
  private PageScalingData mScalingData;

  // cached objects for onDraw
  private static SparseArray<Paint> mSparsePaintArray = new SparseArray<Paint>();
  private RectF mScaledRect = new RectF();
  private Set<String> mAlreadyHighlighted = new HashSet<String>();

  // Params for drawing text
  private OverlayParams mOverlayParams = null;
  private Rect mPageBounds = null;
  private boolean mDidDraw = false;
  private Map<String, List<AyahBounds>> mCoordinatesData;

  public HighlightingImageView(Context context) {
    super(context);
  }

  public HighlightingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void unHighlight(int sura, int ayah, HighlightType type) {
    Set<String> highlights = mCurrentHighlights.get(type);
    if (highlights != null && highlights.remove(sura + ":" + ayah)) {
      invalidate();
    }
  }

  public void unHighlight(HighlightType type) {
    mCurrentHighlights.remove(type);
    invalidate();
  }

  public void setCoordinateData(Map<String, List<AyahBounds>> data) {
    mCoordinatesData = data;
  }

  public void setNightMode(boolean isNightMode) {
    mIsNightMode = isNightMode;
    adjustNightMode();
  }

  public void setNightModeTextBrightness(int nightModeTextBrightness) {
    mNightModeTextBrightness = nightModeTextBrightness;

    // we need a new color filter now
    mColorFilterOn = false;
  }

  public void highlightAyah(int sura, int ayah, HighlightType type) {
    Set<String> highlights = mCurrentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<String>();
      mCurrentHighlights.put(type, highlights);
    } else if (!type.isMultipleHighlightsAllowed()) {
      // If multiple highlighting not allowed (e.g. audio) clear all others of this type first
      highlights.clear();
    }
    highlights.add(sura + ":" + ayah);
  }

  public AyahBounds getYBoundsForHighlight(int sura, int ayah) {
    if (mCoordinatesData == null || mCoordinatesData.get(sura + ":" + ayah) == null) {
      return null;
    }

    Integer upperBound = null;
    Integer lowerBound = null;
    for (AyahBounds bounds : mCoordinatesData.get(sura + ":" + ayah)) {
      if (upperBound == null || bounds.getMinY() < upperBound) {
        upperBound = bounds.getMinY();
      }

      if (lowerBound == null || bounds.getMaxY() > lowerBound) {
        lowerBound = bounds.getMaxY();
      }
    }

    AyahBounds yBounds = null;
    if (upperBound != null && lowerBound != null) {
      yBounds = new AyahBounds(0, 0, 0, upperBound, 0, lowerBound);
    }
    return yBounds;
  }

  @Override
  public void setImageDrawable(Drawable bitmap) {
    // clear the color filter before setting the image
    clearColorFilter();
    // this allows the filter to be enabled again if needed
    mColorFilterOn = false;

    super.setImageDrawable(bitmap);
    mScalingData = null;
    if (bitmap != null) {
      adjustNightMode();
    }
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
    }
    else if (!mIsNightMode && mColorFilterOn) {
      clearColorFilter();
      mColorFilterOn = false;
    }

    invalidate();
  }

  private static class PageScalingData {
    float screenRatio, pageRatio, scaledPageHeight, scaledPageWidth,
        widthFactor, heightFactor, offsetX, offsetY;

    private PageScalingData(Drawable page, int width, int height) {
      screenRatio = (1.0f * height) / (1.0f * width);
      pageRatio = (float) (1.0 * page.getIntrinsicHeight() /
          page.getIntrinsicWidth());
      // depending on whether or not you will have a top or bottom offset
      if (screenRatio < pageRatio) {
        scaledPageHeight = height;
        scaledPageWidth = (float) (1.0 * height /
            page.getIntrinsicHeight() * page.getIntrinsicWidth());
      } else {
        scaledPageWidth = width;
        scaledPageHeight = (float) (1.0 * width /
            page.getIntrinsicWidth() * page.getIntrinsicHeight());
      }

      widthFactor = scaledPageWidth / page.getIntrinsicWidth();
      heightFactor = scaledPageHeight / page.getIntrinsicHeight();

      offsetX = (width - scaledPageWidth) / 2;
      offsetY = (height - scaledPageHeight) / 2;
    }
  }

  private static class OverlayParams {
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
    if (mPageBounds == null) {
      return;
    }

    mOverlayParams = new OverlayParams();
    mOverlayParams.suraText = QuranInfo.getSuraNameFromPage(
        getContext(), page, true);
    mOverlayParams.juzText = QuranInfo.getJuzString(getContext(), page);
    mOverlayParams.pageText = QuranUtils.getLocalizedNumber(
        getContext(), page);
    mOverlayParams.showOverlay = show;

    if (show && !mDidDraw) {
      invalidate();
    }
  }

  public void setPageBounds(Rect rect) {
    mPageBounds = rect;
  }

  private boolean initOverlayParams() {
    if (mOverlayParams == null || mPageBounds == null) {
      return false;
    }

    // Overlay params previously initiated; skip
    if (mOverlayParams.init) {
      return true;
    }

    Drawable page = this.getDrawable();
    if (page == null) {
      return false;
    }
    PageScalingData scalingData = new PageScalingData(page, getWidth(), getHeight());

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
    float textHeight = fm.bottom - fm.top;

    // Text size scale based on the available 'header' and 'footer' space
    // (i.e. gap between top/bottom of screen and actual start of the
    // 'bitmap')
    float scale = scalingData.offsetY / textHeight;

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
            mPageBounds.bottom * scalingData.heightFactor);
        float emptyY = Math.min(emptyYTop, emptyYBottom);
        scale = Math.min(emptyY / textHeight, 1.0f);
      }
      // Set the scaled text size, and update the metrics
      mOverlayParams.paint.setTextSize(MAX_FONT_SIZE * scale);
      fm = mOverlayParams.paint.getFontMetrics();
    }

    // Calculate where the text's baseline should be
    // (for top text and bottom text)
    // (p.s. parts of the glyphs will be below the baseline such as a
    // 'y' or 'ي')
    mOverlayParams.topBaseline = -fm.top;
    mOverlayParams.bottomBaseline = getHeight() - fm.bottom;

    // Calculate the horizontal margins off the edge of screen
    mOverlayParams.offsetX = scalingData.offsetX
        + (getWidth() - mPageBounds.width() * scalingData.widthFactor) / 2.0f;

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
        getWidth() - mOverlayParams.offsetX, mOverlayParams.topBaseline,
        mOverlayParams.paint);
    mOverlayParams.paint.setTextAlign(Align.CENTER);
    canvas.drawText(mOverlayParams.pageText,
        getWidth() / 2.0f, mOverlayParams.bottomBaseline,
        mOverlayParams.paint);
    mDidDraw = true;
  }

  private Paint getPaintForHighlightType(HighlightType type) {
    int color = type.getColor(getContext());
    Paint paint = mSparsePaintArray.get(color);
    if (paint == null) {
      paint = new Paint();
      paint.setColor(color);
      mSparsePaintArray.put(color, paint);
    }
    return paint;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    // Draw overlay text
    mDidDraw = false;
    if (mOverlayParams != null && mOverlayParams.showOverlay) {
      try {
        overlayText(canvas);
      } catch (Exception e) {
      }
    }
    // Draw each ayah highlight
    if (mCoordinatesData != null && !mCurrentHighlights.isEmpty() && this.getDrawable() != null) {
      if (mScalingData == null) {
        mScalingData = new PageScalingData(this.getDrawable(), getWidth(), getHeight());
      }
      mAlreadyHighlighted.clear();
      for (Map.Entry<HighlightType, Set<String>> entry : mCurrentHighlights.entrySet()) {
        Paint paint = getPaintForHighlightType(entry.getKey());
        for (String ayah : entry.getValue()) {
           if (mAlreadyHighlighted.contains(ayah)) continue;
           List<AyahBounds> rangesToDraw = mCoordinatesData.get(ayah);
           if (rangesToDraw != null && !rangesToDraw.isEmpty()) {
             for (AyahBounds b : rangesToDraw) {
               mScaledRect.set(b.getMinX() * mScalingData.widthFactor,
                   b.getMinY() * mScalingData.heightFactor,
                   b.getMaxX() * mScalingData.widthFactor,
                   b.getMaxY() * mScalingData.heightFactor);
               mScaledRect.offset(mScalingData.offsetX, mScalingData.offsetY);
               canvas.drawRect(mScaledRect, paint);
             }
             mAlreadyHighlighted.add(ayah);
           }
        }
      }
    }
  }

  public float[] getPageXY(float screenX, float screenY) {
    Drawable page = this.getDrawable();
    if (page == null)
      return null;
    PageScalingData scalingData = new PageScalingData(page, getWidth(), getHeight());
    float pageX = screenX / scalingData.widthFactor - scalingData.offsetX;
    float pageY = screenY / scalingData.heightFactor - scalingData.offsetY;
    return new float[]{pageX, pageY};
  }
}
