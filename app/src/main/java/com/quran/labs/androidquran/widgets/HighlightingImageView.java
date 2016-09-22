package com.quran.labs.androidquran.widgets;

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
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.SparseArray;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.util.QuranUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class HighlightingImageView extends RecyclingImageView {

  private static int sOverlayTextColor = -1;
  private static int sHeaderFooterSize;
  private static int sHeaderFooterFontSize;
  private static int sScrollableHeaderFooterSize;
  private static int sScrollableHeaderFooterFontSize;

  // Sorted map so we use highest priority highlighting when iterating
  private SortedMap<HighlightType, Set<String>> mCurrentHighlights = new TreeMap<>();

  private boolean mIsNightMode;
  private boolean mColorFilterOn;
  private int mNightModeTextBrightness = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;

  // cached objects for onDraw
  private static SparseArray<Paint> mSparsePaintArray = new SparseArray<>();
  private RectF mScaledRect = new RectF();
  private Set<String> mAlreadyHighlighted = new HashSet<>();

  // Params for drawing text
  private int fontSize;
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
      sOverlayTextColor = ContextCompat.getColor(context, R.color.overlay_text_color);
      sHeaderFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size);
      sScrollableHeaderFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size_scrollable);
      sHeaderFooterFontSize = res.getDimensionPixelSize(R.dimen.page_overlay_font_size);
      sScrollableHeaderFooterFontSize =
          res.getDimensionPixelSize(R.dimen.page_overlay_font_size_scrollable);
    }
  }

  public void setIsScrollable(boolean scrollable) {
    int topBottom = scrollable ? sScrollableHeaderFooterSize : sHeaderFooterSize;
    setPadding(getPaddingLeft(), topBottom, getPaddingRight(), topBottom);
    fontSize = scrollable ? sScrollableHeaderFooterFontSize : sHeaderFooterFontSize;
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
    Paint paint = null;
    float offsetX;
    float topBaseline;
    float bottomBaseline;
    String suraText = null;
    String juzText = null;
    String pageText = null;
    String rub3Text = null;
  }

    // same logic used in displayMarkerPopup method
    public static String displayRub3(Context context, int page)
    {
        int rub3 = QuranInfo.getRub3FromPage(page);
        int hizb = (rub3 / 4) + 1;
        StringBuilder sb = new StringBuilder();
        if (rub3 == -1) {
            return "";
        }
        int remainder = rub3 % 4;
        if (remainder == 1) {
            sb.append(context.getString(R.string.quran_rob3)).append(' ');
        } else if (remainder == 2) {
            sb.append(context.getString(R.string.quran_nos)).append(' ');
        } else if (remainder == 3) {
            sb.append(context.getString(R.string.quran_talt_arb3)).append(' ');
        }
        sb.append(context.getString(R.string.quran_hizb)).append(' ')
                .append(QuranUtils.getLocalizedNumber(context, hizb));

        return sb.toString();
    }

  public void setOverlayText(String suraText, String juzText, String pageText,String rub3Text) {
    // Calculate page bounding rect from ayahinfo db
    if (mPageBounds == null) {
      return;
    }

    mOverlayParams = new OverlayParams();
    mOverlayParams.suraText = suraText;
    mOverlayParams.juzText = juzText;
    mOverlayParams.pageText = pageText;
    mOverlayParams.rub3Text=rub3Text;
    mOverlayParams.paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
    mOverlayParams.paint.setTextSize(fontSize);

    if (!mDidDraw) {
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

    int overlayColor = sOverlayTextColor;
    if (mIsNightMode) {
      overlayColor = Color.rgb(mNightModeTextBrightness,
          mNightModeTextBrightness, mNightModeTextBrightness);
    }
    mOverlayParams.paint.setColor(overlayColor);

    // Use font metrics to calculate the maximum possible height of the text
    FontMetrics fm = mOverlayParams.paint.getFontMetrics();

    final RectF mappedRect = new RectF();
    matrix.mapRect(mappedRect, mPageBounds);

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
     // Write the current rub3 text at the top middle of the page
     if (!mOverlayParams.rub3Text.equals("")) {
          mOverlayParams.paint.setTextAlign(Align.CENTER);
          canvas.drawText(mOverlayParams.rub3Text,
                  getWidth() / 2.0f, mOverlayParams.topBaseline,
                  mOverlayParams.paint);
      }
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
    if (mOverlayParams != null) {
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
               mScaledRect.offset(0, getPaddingTop());
               canvas.drawRect(mScaledRect, paint);
             }
             mAlreadyHighlighted.add(ayah);
           }
        }
      }
    }
  }
}
