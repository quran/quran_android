package com.quran.labs.androidquran.widgets;

import android.animation.Animator;
import android.animation.RectEvaluator;
import android.animation.TimeInterpolator;
import android.animation.TypeEvaluator;
import android.animation.ValueAnimator;
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
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.page.common.data.AyahBounds;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.draw.ImageDrawHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

public class HighlightingImageView extends AppCompatImageView {

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
  private PageCoordinates pageCoordinates;
  private AyahCoordinates ayahCoordinates;
  private Set<ImageDrawHelper> imageDrawHelpers;
  private Map<String, List<AyahBounds>>  floatableAyahCoordinates = new HashMap<>();
  final static String TAG = "MMR";

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

  public void setPageData(PageCoordinates pageCoordinates, Set<ImageDrawHelper> imageDrawHelpers) {
    this.imageDrawHelpers = imageDrawHelpers;
    this.pageCoordinates = pageCoordinates;
  }

  public void setAyahData(AyahCoordinates ayahCoordinates) {
    this.ayahCoordinates = ayahCoordinates;
  }

  public void setNightMode(boolean isNightMode, int textBrightness) {
    this.isNightMode = isNightMode;
    if (isNightMode) {
      nightModeTextBrightness = textBrightness;
      // we need a new color filter now
      isColorFilterOn = false;
    }
    // Re-init overlay params color based on the new night mode setting
    initOverlayParamsColor();
    adjustNightMode();
  }

  private void highlightFloatableAyah(Set<String> highlights, String currentSurahAyah) {
    Log.d(TAG, "Now setting up animation for "+currentSurahAyah);
    String previousSurahAyah = currentSurahAyah;
    for(String surahAyahs: highlights) {
      previousSurahAyah = surahAyahs;
    }
    highlights.clear();
    final Map<String, List<AyahBounds>> coordinatesData = ayahCoordinates == null ? null :
        ayahCoordinates.getAyahCoordinates();
    if(coordinatesData == null
        || coordinatesData.get(previousSurahAyah) == null
        || coordinatesData.get(currentSurahAyah) == null) {
      // can't setup animation, if coordinates are not known beforehand
      highlights.add(currentSurahAyah);
      return;
    }
    final String ayahTransition = previousSurahAyah+"->"+currentSurahAyah;
    Log.d(TAG, "Now setting up animation for "+ayahTransition);
    List<AyahBounds> previousAyahBoundsList = new ArrayList<>(coordinatesData.get(previousSurahAyah));
    List<AyahBounds> currentAyahBoundsList = new ArrayList<>(coordinatesData.get(currentSurahAyah));
    highlights.add(ayahTransition);

    // add animator
    ValueAnimator animator = ValueAnimator.ofObject(new TypeEvaluator() {

      private void normalizeAyahBoundsList(List<AyahBounds> start, List<AyahBounds> end) {
        // this function takes two unequal length lists and tries to normalize them

        int startSize = start.size();
        int endSize = end.size();
        int minSize = Math.min(startSize, endSize);
        int maxSize = Math.max(startSize, endSize);
        List<AyahBounds> minList = startSize < endSize? start : end;
        int diff = maxSize - minSize;

        RectF rectToBeDivided = minList.get(minSize-1).getBounds();
        float oLeft = rectToBeDivided.left;
        float oRight = rectToBeDivided.right;
        float oTop = rectToBeDivided.top;
        float oBottom = rectToBeDivided.bottom;
        minList.remove(minSize-1);
        float part = (oRight-oLeft) /(diff+1);
        for(int i=0; i<(diff+1); ++i) {
          float left = oLeft + part*i;
          float right = left + part;
          RectF rect = new RectF(left, oTop, right, oBottom);
          AyahBounds ayahBounds = new AyahBounds(0, 0, rect);
          minList.add(ayahBounds);
        }
      }

      @Override
      public Object evaluate(float fraction, Object startObject, Object endObject) {
        List<AyahBounds> start = (List<AyahBounds>)startObject;
        List<AyahBounds> end = (List<AyahBounds>)endObject;

        if(start.size() != end.size()) {
          normalizeAyahBoundsList(start, end);
        }

        int size = start.size();
        List<AyahBounds> result = new ArrayList<>(size);

        for(int i=0; i<size; ++i) {
          RectF startValue = start.get(i).getBounds();
          RectF endValue = end.get(i).getBounds();
          float left = startValue.left + (endValue.left - startValue.left) * fraction;
          float top = startValue.top + (endValue.top - startValue.top) * fraction;
          float right = startValue.right + (endValue.right - startValue.right) * fraction;
          float bottom = startValue.bottom + (endValue.bottom - startValue.bottom) * fraction;
          AyahBounds intermediateBounds = new AyahBounds(0,0, new RectF(left, top, right, bottom));
          result.add(intermediateBounds);
        }
        return result;
      }
    }, previousAyahBoundsList, currentAyahBoundsList);
    animator.setDuration(500);

    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        List<AyahBounds> value = (List<AyahBounds>) animation.getAnimatedValue();
        floatableAyahCoordinates.put(ayahTransition, value);
        invalidate();
      }
    });
    animator.addListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {

      }

      @Override
      public void onAnimationEnd(Animator animation) {
        floatableAyahCoordinates.remove(ayahTransition);
        highlights.remove(ayahTransition);
        highlights.add(currentSurahAyah);
      }

      @Override
      public void onAnimationCancel(Animator animation) {
        floatableAyahCoordinates.remove(ayahTransition);
        highlights.remove(ayahTransition);
      }

      @Override
      public void onAnimationRepeat(Animator animation) {

      }
    });
    animator.setInterpolator(new AccelerateDecelerateInterpolator());
    animator.start();
  }

  private boolean shouldFloatHighlight(Set<String> highlights, HighlightType type, int sura, int ayah) {
    // TODO: this should really be handled by HighlightType.HighlightAnimationConfig
    // TODO: add more conditions, restricting float animation
    Log.d("MMR", "Should float highlight "+sura + ":" +ayah);

    // only animating AUDIO highlights, for now
    if(!type.equals(HighlightType.AUDIO)) {
      return false;
    }

    // can only animate from one ayah to another, for now
    if(highlights.size() != 1) {
      Log.d("MMR", "highlight size not 1 but"+highlights.size());
      return false;
    }

    String currentSurahAyah = sura + ":" + ayah;
    String previousSurahAyah = currentSurahAyah;
    for(String surahAyahs: highlights) {
      previousSurahAyah = surahAyahs;
    }
    if(currentSurahAyah.equals(previousSurahAyah)) {
      // can't animate to the same location
      return false;
    }
    // if ayah on different pages then return false (what about double pages?) (but the algorithm should work regardless)
    // if ayah not consecutive, then also return false (but the algorithm should work regardless)
    return true;
  }

  public void highlightAyah(int sura, int ayah, HighlightType type) {
    Set<String> highlights = currentHighlights.get(type);
    if (highlights == null) {
      highlights = new HashSet<>();
      currentHighlights.put(type, highlights);
      highlights.add(sura + ":" + ayah);
    } else if (!type.isMultipleHighlightsAllowed()) {
      // If multiple highlighting not allowed (e.g. audio)
      // clear all others of this type first
      // only if highlight type is floatable
      if(shouldFloatHighlight(highlights, type, sura, ayah)) {
        highlightFloatableAyah(highlights, sura + ":" + ayah);
      } else {
        highlights.clear();
        highlights.add(sura + ":" + ayah);
      }
    }
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

    initOverlayParamsColor();

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

  private void initOverlayParamsColor() {
    if (overlayParams == null) {
      return;
    }
    int overlayColor = overlayTextColor;
    if (isNightMode) {
      overlayColor = Color.rgb(nightModeTextBrightness,
          nightModeTextBrightness, nightModeTextBrightness);
    }
    overlayParams.paint.setColor(overlayColor);
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
    final Map<String, List<AyahBounds>> coordinatesData = ayahCoordinates == null ? null :
        ayahCoordinates.getAyahCoordinates();
    if (coordinatesData != null && !currentHighlights.isEmpty()) {
      alreadyHighlighted.clear();
      for (Map.Entry<HighlightType, Set<String>> entry : currentHighlights.entrySet()) {
        Paint paint = getPaintForHighlightType(entry.getKey());
        for (String ayah : entry.getValue()) {
           if (alreadyHighlighted.contains(ayah)) continue;
           List<AyahBounds> rangesToDraw = coordinatesData.get(ayah);
           if(rangesToDraw == null || rangesToDraw.isEmpty()) {
             rangesToDraw = floatableAyahCoordinates.get(ayah);
           }
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

    // run additional image draw helpers if any
    if (imageDrawHelpers != null && pageCoordinates != null) {
      for (ImageDrawHelper imageDrawHelper : imageDrawHelpers) {
        imageDrawHelper.draw(pageCoordinates, canvas, this);
      }
    }
  }
}
