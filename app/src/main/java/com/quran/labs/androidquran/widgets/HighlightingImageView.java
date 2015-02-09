package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranUtils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.FontMetrics;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.util.SparseArray;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class HighlightingImageView extends RecyclingImageView {

  private static int sOverlayTextColor = -1;
  private static float sMaxFontSize = 0;
  private static float sMinFontSize = 0;

  // Sorted map so we use highest priority highlighting when iterating
  private SortedMap<HighlightType, Set<String>> mCurrentHighlights =
      new TreeMap<>();
  private boolean mColorFilterOn = false;
  private boolean mIsNightMode = false;
  private int mNightModeTextBrightness =
      Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;

  // cached objects for onDraw
  private static SparseArray<Paint> mSparsePaintArray = new SparseArray<>();
  private RectF mScaledRect = new RectF();
  private Set<String> mAlreadyHighlighted = new HashSet<>();

  // Params for drawing text
  private OverlayParams mOverlayParams = null;
  private RectF mPageBounds = null;
  private boolean mDidDraw = false;
  private Map<String, List<AyahBounds>> mCoordinatesData;

  public HighlightingImageView(Context context) {
    this(context, null);
  }

  public HighlightingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    if (sOverlayTextColor == -1) {
      final Resources res = context.getResources();
      sOverlayTextColor = res.getColor(R.color.overlay_text_color);
      sMinFontSize = res.getDimensionPixelSize(R.dimen.min_overlay_font_size);
      sMaxFontSize = res.getDimensionPixelSize(R.dimen.max_overlay_font_size);
    }
  }

  public void unHighlight(int sura, int ayah, HighlightType type) {
    Set<String> highlights = mCurrentHighlights.get(type);
    if (highlights != null && highlights.remove(sura + ":" + ayah)) {
      invalidate();
    }
  }

  public void highlightAyat(Set<String> ayahKeys, HighlightType type) {
    Set<String> highlights = mCurrentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<>();
      mCurrentHighlights.put(type, highlights);
    }
    highlights.addAll(ayahKeys);
  }

  public void unHighlight(HighlightType type) {
    mCurrentHighlights.remove(type);
    invalidate();
  }

  public void setCoordinateData(Map<String, List<AyahBounds>> data) {
    mCoordinatesData = data;
  }

  public void setNightMode(boolean isNightMode, int textBrightness) {
    mIsNightMode = isNightMode;
    if (isNightMode) {
      mNightModeTextBrightness = textBrightness;
      // we need a new color filter now
      mColorFilterOn = false;
    }
    adjustNightMode();
  }

  public void highlightAyah(int sura, int ayah, HighlightType type) {
    Set<String> highlights = mCurrentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<>();
      mCurrentHighlights.put(type, highlights);
    } else if (!type.isMultipleHighlightsAllowed()) {
      // If multiple highlighting not allowed (e.g. audio)
      // clear all others of this type first
      highlights.clear();
    }
    highlights.add(sura + ":" + ayah);
  }

  @Override
  public void setImageDrawable(Drawable bitmap) {
    // clear the color filter before setting the image
    clearColorFilter();
    // this allows the filter to be enabled again if needed
    mColorFilterOn = false;

    super.setImageDrawable(bitmap);
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
    } else if (!mIsNightMode) {
      clearColorFilter();
      mColorFilterOn = false;
    }

    invalidate();
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

  public void setPageBounds(RectF rect) {
    mPageBounds = rect;
  }

  private boolean initOverlayParams(Matrix matrix) {
    if (mOverlayParams == null || mPageBounds == null) {
      return false;
    }

    // Overlay params previously initiated; skip
    if (mOverlayParams.init) {
      return true;
    }

    mOverlayParams.paint = new Paint(Paint.ANTI_ALIAS_FLAG
        | Paint.DEV_KERN_TEXT_FLAG);
    mOverlayParams.paint.setTextSize(sMaxFontSize);
    int overlayColor = sOverlayTextColor;
    if (mIsNightMode) {
      overlayColor = Color.rgb(mNightModeTextBrightness,
          mNightModeTextBrightness, mNightModeTextBrightness);
    }
    mOverlayParams.paint.setColor(overlayColor);

    // Use font metrics to calculate the maximum possible height of the text
    FontMetrics fm = mOverlayParams.paint.getFontMetrics();
    float textHeight = fm.bottom - fm.top;

    final RectF mappedRect = new RectF();
    matrix.mapRect(mappedRect, mPageBounds);

    // Text size scale based on the available 'header' and 'footer' space
    // (i.e. gap between top/bottom of screen and actual start of the
    // 'bitmap')
    float scale = mappedRect.top / textHeight;

    // If the height of the drawn text might be greater than the available
    // gap... scale down the text size by the calculated scale
    if (scale < 1.0) {
      // If after scaling the text size will be less than the minimum
      // size... then don't draw.
      final float targetSize = sMaxFontSize * scale;
      if (targetSize < sMinFontSize) {
        mOverlayParams.init = true;
        mOverlayParams.showOverlay = false;
        return true;
      }
      // Set the scaled text size, and update the metrics
      mOverlayParams.paint.setTextSize(targetSize);
      fm = mOverlayParams.paint.getFontMetrics();
    }

    // Calculate where the text's baseline should be
    // (for top text and bottom text)
    // (p.s. parts of the glyphs will be below the baseline such as a
    // 'y' or 'ي')
    mOverlayParams.topBaseline = -fm.top;
    mOverlayParams.bottomBaseline = getHeight() - fm.bottom;

    // Calculate the horizontal margins off the edge of screen
    mOverlayParams.offsetX = Math.min(
        mappedRect.left, getWidth() - mappedRect.right);

    mOverlayParams.init = true;
    return true;
  }

  private void overlayText(Canvas canvas, Matrix matrix) {
    if (mOverlayParams == null || !initOverlayParams(matrix)) {
      return;
    }

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
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (mOverlayParams != null) {
      mOverlayParams.init = false;
    }
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas) {
    super.onDraw(canvas);

    final Drawable d = getDrawable();
    if (d == null) {
      // no image, forget it.
      return;
    }

    final Matrix matrix = getImageMatrix();

    // Draw overlay text
    mDidDraw = false;
    if (mOverlayParams != null && mOverlayParams.showOverlay) {
      overlayText(canvas, matrix);
    }

    // Draw each ayah highlight
    if (mCoordinatesData != null && !mCurrentHighlights.isEmpty()) {
      mAlreadyHighlighted.clear();
      for (Map.Entry<HighlightType, Set<String>> entry : mCurrentHighlights.entrySet()) {
        Paint paint = getPaintForHighlightType(entry.getKey());
        for (String ayah : entry.getValue()) {
           if (mAlreadyHighlighted.contains(ayah)) continue;
           List<AyahBounds> rangesToDraw = mCoordinatesData.get(ayah);
           if (rangesToDraw != null && !rangesToDraw.isEmpty()) {
             for (AyahBounds b : rangesToDraw) {
               matrix.mapRect(mScaledRect, b.getBounds());
               canvas.drawRect(mScaledRect, paint);
             }
             mAlreadyHighlighted.add(ayah);
           }
        }
      }
    }
  }
}
