package com.quran.labs.androidquran.view;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Px;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;

/**
 * Generic layout class for a single page of the Quran.
 * <p>
 * The actual contents of the page are determined by the subclass, which specifies the view to
 * display with {@link #generateContentView(Context, boolean)}. If the device is landscape and
 * {@link #shouldWrapWithScrollView()} returns true, then the view is wrapped with an
 * {@link ObservableScrollView}. Margins get displayed around the page, along with an appropriate
 * background.
 */
public abstract class QuranPageLayout extends QuranPageWrapperLayout
    implements ObservableScrollView.OnScrollListener {

  @IntDef( { BorderMode.HIDDEN, BorderMode.LIGHT, BorderMode.DARK, BorderMode.LINE } )
  @interface BorderMode {
    int HIDDEN = 0;
    int LIGHT = 1;
    int DARK = 2;
    int LINE = 3;
  }

  private static PaintDrawable leftGradient;
  private static PaintDrawable rightGradient;
  private static int gradientForNumberOfPages;
  private static boolean areGradientsLandscape;
  private static BitmapDrawable leftPageBorder;
  private static BitmapDrawable rightPageBorder;
  private static BitmapDrawable leftPageBorderNight;
  private static BitmapDrawable rightPageBorderNight;

  private static int lineColor;
  private static ShapeDrawable lineDrawable;

  protected Context context;
  protected PageController pageController;
  protected int pageNumber;
  protected boolean shouldHideLine;
  protected boolean isFullWidth;
  private int skippedPages;

  private ObservableScrollView scrollView;
  private @BorderMode int leftBorder;
  private @BorderMode int rightBorder;
  private View innerView;
  private int viewPaddingSmall;
  private int viewPaddingLarge;

  public QuranPageLayout(Context context) {
    super(context);
    this.context = context;
  }

  public void initialize() {
    ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_LTR);
    Resources resources = context.getResources();
    final boolean isLandscape =
        resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    innerView = generateContentView(context, isLandscape);
    viewPaddingSmall = resources.getDimensionPixelSize(R.dimen.page_margin_small);
    viewPaddingLarge = resources.getDimensionPixelSize(R.dimen.page_margin_large);

    LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    if (isLandscape && shouldWrapWithScrollView()) {
      scrollView = new ObservableScrollView(context);
      scrollView.setFillViewport(true);
      addView(scrollView, lp);
      scrollView.addView(innerView, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      scrollView.setOnScrollListener(this);
    } else {
      addView(innerView, lp);
    }

    if (areGradientsLandscape != isLandscape) {
      leftGradient = null;
      rightGradient = null;
      areGradientsLandscape = isLandscape;
    }

    if (lineDrawable == null) {
      lineDrawable = new ShapeDrawable(new RectShape());
      lineDrawable.setIntrinsicWidth(1);
      lineDrawable.setIntrinsicHeight(1);

      // these bitmaps are 11x1, so fairly small to keep both day and night versions around
      leftPageBorder = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.border_left));
      leftPageBorderNight = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.night_left_border));
      rightPageBorder = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.border_right));
      rightPageBorderNight = new BitmapDrawable(resources,
          BitmapFactory.decodeResource(resources, R.drawable.night_right_border));
    }

    updateGradients();
    setWillNotDraw(false);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    View view = resolveView();
    if (view != null) {
      int width = MeasureSpec.getSize(widthMeasureSpec);
      int height = MeasureSpec.getSize(heightMeasureSpec);
      int leftLineWidth = leftBorder == BorderMode.LINE ? 1 : getBorderWidth(leftPageBorder);
      int rightLineWidth = rightBorder == BorderMode.HIDDEN ?
          0 : getBorderWidth(rightPageBorder);
      width = width - (leftLineWidth + rightLineWidth);
      if (!isFullWidth) {
        int headerFooterHeight = 0;
        width = width - (viewPaddingSmall + viewPaddingLarge);
        height = height - 2 * headerFooterHeight;
      }
      view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
          MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }
  }

  private int getBorderWidth(Drawable drawable) {
    if (drawable == lineDrawable)
      return lineDrawable.getIntrinsicWidth();

    // enlarge the book side indicator to a sensible device-independent width
    return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
        13f,
        getResources().getDisplayMetrics());
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    View view = resolveView();
    if (view != null) {
      int width = getMeasuredWidth();
      int height = getMeasuredHeight();
      @Px int leftLineWidth = leftBorder == BorderMode.LINE ?
          1 : getBorderWidth(leftPageBorder);
      @Px int rightLineWidth = rightBorder == BorderMode.HIDDEN ?
          0 : getBorderWidth(rightPageBorder);
      int headerFooterHeight = 0;
      view.layout(leftLineWidth, headerFooterHeight,
          width - rightLineWidth, height - headerFooterHeight);
      super.onLayout(changed, l, t, r, b);
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    int width = getWidth();
    if (width > 0) {
      int height = getHeight();
      if (leftBorder != BorderMode.LINE || !shouldHideLine) {
        Drawable left = leftBorder == BorderMode.LINE ? lineDrawable :
            leftBorder == BorderMode.LIGHT ? leftPageBorder : leftPageBorderNight;
        left.setBounds(0, 0, getBorderWidth(left), height);
        left.draw(canvas);
      }

      if (rightBorder != BorderMode.HIDDEN) {
        Drawable right = rightBorder == BorderMode.LIGHT ? rightPageBorder : rightPageBorderNight;
        right.setBounds(width - getBorderWidth(right), 0, width, height);
        right.draw(canvas);
      }
    }
  }

  protected abstract View generateContentView(@NonNull Context context, boolean isLandscape);

  protected boolean shouldWrapWithScrollView() {
    return true;
  }

  private View resolveView() {
    return scrollView != null ? scrollView : innerView;
  }

  public void setPageController(PageController controller, int pageNumber, int skippedPages) {
    this.pageNumber = pageNumber;
    this.pageController = controller;
    this.skippedPages = skippedPages;
  }

  protected int getPagesVisible() {
    return 1;
  }

  private void updateGradients() {
    int pagesVisible = getPagesVisible();
    if (rightGradient == null || gradientForNumberOfPages != pagesVisible) {
      final WindowManager mgr =
          (WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
      Display display = mgr.getDefaultDisplay();
      int width = QuranDisplayHelper.getWidthKitKat(display);
      width = width / pagesVisible;
      leftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      rightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      gradientForNumberOfPages = pagesVisible;
    }
  }

  @Override
  public void updateView(@NonNull QuranSettings quranSettings) {
    super.updateView(quranSettings);
    boolean nightMode = quranSettings.isNightMode();
    int lineColor = Color.BLACK;
    final int nightModeTextBrightness = nightMode ?
        quranSettings.getNightModeTextBrightness() : Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
    if (nightMode) {
      lineColor = Color.argb(nightModeTextBrightness, 255, 255, 255);
    }

    if ((pageNumber + skippedPages) % 2 == 0) {
      leftBorder = nightMode ? BorderMode.DARK : BorderMode.LIGHT;
      rightBorder = BorderMode.HIDDEN;
    } else {
      rightBorder = nightMode ? BorderMode.DARK : BorderMode.LIGHT;
      if (QuranPageLayout.lineColor != lineColor) {
        QuranPageLayout.lineColor = lineColor;
        lineDrawable.getPaint().setColor(lineColor);
      }
      leftBorder = BorderMode.LINE;
    }

    updateBackground(nightMode, quranSettings);
  }

  protected void updateBackground(boolean nightMode, QuranSettings quranSettings) {
    if (nightMode) {
      int bgColor = quranSettings.getNightModeBackgroundBrightness();
      setBackgroundColor(Color.rgb(bgColor,bgColor,bgColor));
    } else if (quranSettings.useNewBackground()) {
      setBackgroundDrawable((pageNumber % 2 == 0 ? leftGradient : rightGradient));
    } else {
      setBackgroundColor(ContextCompat.getColor(context, R.color.page_background));
    }
  }

  @Override
  void handleRetryClicked() {
    if (pageController != null) {
      pageController.handleRetryClicked();
    }
  }

  public int getCurrentScrollY() {
    return scrollView == null ? 0 : scrollView.getScrollY();
  }

  public boolean canScroll() {
    return scrollView != null;
  }

  public void smoothScrollLayoutTo(int y) {
    scrollView.smoothScrollTo(scrollView.getScrollX(), y);
  }

  @Override
  public void onScrollChanged(ObservableScrollView scrollView,
      int x, int y, int oldx, int oldy) {
    if (pageController != null) {
      pageController.onScrollChanged(y);
    }
  }
}
