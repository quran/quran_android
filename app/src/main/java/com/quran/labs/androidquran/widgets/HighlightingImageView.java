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
import android.widget.ImageView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.HighlightType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class HighlightingImageView extends ImageView {

  private static final SparseArray<Paint> SPARSE_PAINT_ARRAY = new SparseArray<>();

  private static int overlayTextColor = -1;
  private static int headerFooterSize;
  private static int headerFooterFontSize;
  private static int scrollableHeaderFooterSize;
  private static int scrollableHeaderFooterFontSize;

  // Sorted map so we use highest priority highlighting when iterating
  private SortedMap<HighlightType, Set<String>> currentHighlights = new TreeMap<>();

  private boolean isNightMode;
  private boolean isColorFilterOn;
  private int nightModeTextBrightness = Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;

  // cached objects for onDraw
  private final RectF scaledRect = new RectF();
  private final Set<String> alreadyHighlighted = new HashSet<>();

  // Params for drawing text
  private int fontSize;
  private OverlayParams overlayParams = null;
  private RectF pageBounds = null;
  private boolean didDraw = false;
  private Map<String, List<AyahBounds>> coordinatesData;

  public HighlightingImageView(Context context) {
    this(context, null);
  }

  public HighlightingImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
    if (overlayTextColor == -1) {
      final Resources res = context.getResources();
      overlayTextColor = ContextCompat.getColor(context, R.color.overlay_text_color);
      headerFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size);
      scrollableHeaderFooterSize = res.getDimensionPixelSize(R.dimen.page_overlay_size_scrollable);
      headerFooterFontSize = res.getDimensionPixelSize(R.dimen.page_overlay_font_size);
      scrollableHeaderFooterFontSize =
          res.getDimensionPixelSize(R.dimen.page_overlay_font_size_scrollable);
    }
  }

  public void setIsScrollable(boolean scrollable) {
    int topBottom = scrollable ? scrollableHeaderFooterSize : headerFooterSize;
    setPadding(getPaddingLeft(), topBottom, getPaddingRight(), topBottom);
    fontSize = scrollable ? scrollableHeaderFooterFontSize : headerFooterFontSize;
  }

  public void unHighlight(int sura, int ayah, HighlightType type) {
    Set<String> highlights = currentHighlights.get(type);
    if (highlights != null && highlights.remove(sura + ":" + ayah)) {
      invalidate();
    }
  }

  public void highlightAyat(Set<String> ayahKeys, HighlightType type) {
    Set<String> highlights = currentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<>();
      currentHighlights.put(type, highlights);
    }
    highlights.addAll(ayahKeys);
  }

  public void unHighlight(HighlightType type) {
    if (!currentHighlights.isEmpty()) {
      currentHighlights.remove(type);
      invalidate();
    }
  }

  public void setCoordinateData(Map<String, List<AyahBounds>> data) {
    coordinatesData = data;
  }

  public void setNightMode(boolean isNightMode, int textBrightness) {
    this.isNightMode = isNightMode;
    if (isNightMode) {
      nightModeTextBrightness = textBrightness;
      // we need a new color filter now
      isColorFilterOn = false;
    }
    adjustNightMode();
  }

  public void highlightAyah(int sura, int ayah, HighlightType type) {
    Set<String> highlights = currentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<>();
      currentHighlights.put(type, highlights);
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
    isColorFilterOn = false;

    super.setImageDrawable(bitmap);
    if (bitmap != null) {
      adjustNightMode();
    }
  }

  public void adjustNightMode() {
    if (isNightMode && !isColorFilterOn) {
      float[] matrix = {
          -1, 0, 0, 0, nightModeTextBrightness,
          0, -1, 0, 0, nightModeTextBrightness,
          0, 0, -1, 0, nightModeTextBrightness,
          0, 0, 0, 1, 0
      };
      setColorFilter(new ColorMatrixColorFilter(matrix));
      isColorFilterOn = true;
    } else if (!isNightMode) {
      clearColorFilter();
      isColorFilterOn = false;
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

  public void setOverlayText(String suraText, String juzText, String pageText, String rub3Text) {
    // Calculate page bounding rect from ayahinfo db
    if (pageBounds == null) {
      return;
    }

    overlayParams = new OverlayParams();
    overlayParams.suraText = suraText;
    overlayParams.juzText = juzText;
    overlayParams.pageText = pageText;
    overlayParams.rub3Text = rub3Text;
    overlayParams.paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
    overlayParams.paint.setTextSize(fontSize);

    if (!didDraw) {
      invalidate();
    }
  }

  public void setPageBounds(RectF rect) {
    pageBounds = rect;
  }

  private boolean initOverlayParams(Matrix matrix) {
    if (overlayParams == null || pageBounds == null) {
      return false;
    }

    // Overlay params previously initiated; skip
    if (overlayParams.init) {
      return true;
    }

    int overlayColor = overlayTextColor;
    if (isNightMode) {
      overlayColor = Color.rgb(nightModeTextBrightness,
          nightModeTextBrightness, nightModeTextBrightness);
    }
    overlayParams.paint.setColor(overlayColor);

    // Use font metrics to calculate the maximum possible height of the text
    FontMetrics fm = overlayParams.paint.getFontMetrics();

    final RectF mappedRect = new RectF();
    matrix.mapRect(mappedRect, pageBounds);

    // Calculate where the text's baseline should be
    // (for top text and bottom text)
    // (p.s. parts of the glyphs will be below the baseline such as a
    // 'y' or 'ي')
    overlayParams.topBaseline = -fm.top;
    overlayParams.bottomBaseline = getHeight() - fm.bottom;

    // Calculate the horizontal margins off the edge of screen
    overlayParams.offsetX = Math.min(
        mappedRect.left, getWidth() - mappedRect.right);

    overlayParams.init = true;
    return true;
  }

  private void overlayText(Canvas canvas, Matrix matrix) {
    if (overlayParams == null || !initOverlayParams(matrix)) {
      return;
    }

    overlayParams.paint.setTextAlign(Align.LEFT);
    canvas.drawText(overlayParams.suraText,
        overlayParams.offsetX, overlayParams.topBaseline,
        overlayParams.paint);
    overlayParams.paint.setTextAlign(Align.CENTER);
    canvas.drawText(overlayParams.pageText,
        getWidth() / 2.0f, overlayParams.bottomBaseline,
        overlayParams.paint);
    // Merge the current rub3 text with the juz' text
    overlayParams.paint.setTextAlign(Align.RIGHT);
    canvas.drawText(overlayParams.juzText + overlayParams.rub3Text,
        getWidth() - overlayParams.offsetX, overlayParams.topBaseline,
        overlayParams.paint);
    didDraw = true;
  }

  private Paint getPaintForHighlightType(HighlightType type) {
    int color = type.getColor(getContext());
    Paint paint = SPARSE_PAINT_ARRAY.get(color);
    if (paint == null) {
      paint = new Paint();
      paint.setColor(color);
      SPARSE_PAINT_ARRAY.put(color, paint);
    }
    return paint;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (overlayParams != null) {
      overlayParams.init = false;
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
    didDraw = false;
    if (overlayParams != null) {
      overlayText(canvas, matrix);
    }

    // Draw each ayah highlight
    if (coordinatesData != null && !currentHighlights.isEmpty()) {
      alreadyHighlighted.clear();
      for (Map.Entry<HighlightType, Set<String>> entry : currentHighlights.entrySet()) {
        Paint paint = getPaintForHighlightType(entry.getKey());
        for (String ayah : entry.getValue()) {
           if (alreadyHighlighted.contains(ayah)) continue;
           List<AyahBounds> rangesToDraw = coordinatesData.get(ayah);
           if (rangesToDraw != null && !rangesToDraw.isEmpty()) {
             for (AyahBounds b : rangesToDraw) {
               matrix.mapRect(scaledRect, b.getBounds());
               scaledRect.offset(0, getPaddingTop());
               canvas.drawRect(scaledRect, paint);
             }
             alreadyHighlighted.add(ayah);
           }
        }
      }
    }
  }
}
