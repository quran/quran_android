package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;

public abstract class QuranPageLayout extends FrameLayout
    implements ObservableScrollView.OnScrollListener {
  private static PaintDrawable leftGradient;
  private static PaintDrawable rightGradient;
  private static int gradientForNumberOfPages;
  private static boolean areGradientsLandscape;

  private static int lineColor;
  private static ShapeDrawable lineDrawable;

  protected Context context;
  protected PageController pageController;
  protected int pageNumber;

  private boolean isNightMode;
  private ObservableScrollView scrollView;
  private ImageView leftBorder;
  private ImageView rightBorder;
  private View errorLayout;
  private TextView errorText;
  private View innerView;
  private int viewPaddingSmall;
  private int viewPaddingLarge;

  public QuranPageLayout(Context context) {
    super(context);
    this.context = context;
    ViewCompat.setLayoutDirection(this, ViewCompat.LAYOUT_DIRECTION_LTR);
    Resources resources = context.getResources();
    final boolean isLandscape =
        resources.getConfiguration().orientation ==
        Configuration.ORIENTATION_LANDSCAPE;
    innerView = generateContentView(context);
    viewPaddingSmall = resources.getDimensionPixelSize(R.dimen.page_margin_small);
    viewPaddingLarge = resources.getDimensionPixelSize(R.dimen.page_margin_large);

    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
    lp.gravity = Gravity.CENTER;
    if (isLandscape && shouldWrapWithScrollView()) {
      scrollView = new ObservableScrollView(context);
      scrollView.setFillViewport(true);
      addView(scrollView, lp);
      scrollView.addView(innerView, LayoutParams.MATCH_PARENT,
          LayoutParams.WRAP_CONTENT);
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
    }
  }

  protected abstract View generateContentView(Context context);
  protected abstract void setContentNightMode(boolean nightMode, int textBrightness);

  protected boolean shouldWrapWithScrollView() {
    return true;
  }

  public void setPageController(PageController controller, int pageNumber) {
    this.pageNumber = pageNumber;
    this.pageController = controller;
  }

  public void updateView(boolean nightMode, boolean useNewBackground, int pagesVisible) {
    if (rightGradient == null || gradientForNumberOfPages != pagesVisible) {
      final WindowManager mgr =
          (WindowManager) context.getApplicationContext()
              .getSystemService(Context.WINDOW_SERVICE);
      Display display = mgr.getDefaultDisplay();
      int width = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
          QuranDisplayHelper.getWidthKitKat(display) : display.getWidth();
      width = width / pagesVisible;
      leftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      rightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      gradientForNumberOfPages = pagesVisible;
    }

    isNightMode = nightMode;
    int lineColor = Color.BLACK;
    final int leftBorderImageId = nightMode ?
        R.drawable.night_left_border : R.drawable.border_left;
    final int rightBorderImageId = nightMode ?
        R.drawable.night_right_border : R.drawable.border_right;
    final int nightModeTextBrightness = nightMode ?
        QuranSettings.getInstance(context).getNightModeTextBrightness() :
        Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
    if (nightMode) {
      lineColor = Color.argb(nightModeTextBrightness, 255, 255, 255);
    }

    if (leftBorder == null) {
      leftBorder = new ImageView(context);
      final FrameLayout.LayoutParams params =
          new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.MATCH_PARENT);
      params.gravity = GravityCompat.START;
      addView(leftBorder, params);
    }

    if (pageNumber % 2 == 0) {
      leftBorder.setBackgroundResource(leftBorderImageId);
      if (rightBorder != null) {
        rightBorder.setVisibility(GONE);
      }
    } else {
      if (rightBorder == null) {
        rightBorder = new ImageView(context);
        final FrameLayout.LayoutParams params =
            new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = GravityCompat.END;
        addView(rightBorder, params);
      }
      rightBorder.setVisibility(VISIBLE);
      rightBorder.setBackgroundResource(rightBorderImageId);
      if (QuranPageLayout.lineColor != lineColor) {
        QuranPageLayout.lineColor = lineColor;
        lineDrawable.getPaint().setColor(lineColor);
      }
      leftBorder.setBackgroundDrawable(lineDrawable);
    }
    setContentNightMode(nightMode, nightModeTextBrightness);

    if (nightMode) {
      setBackgroundColor(Color.BLACK);
    } else if (useNewBackground) {
      setBackgroundDrawable((pageNumber % 2 == 0 ? leftGradient : rightGradient));
    } else {
      setBackgroundColor(ContextCompat.getColor(context, R.color.page_background));
    }

    if (errorText != null) {
      updateErrorTextColor();
    }

    // set a margin on the page itself so that it can never overlap the
    // left or right borders.
    final View innerView = scrollView == null ? this.innerView : scrollView;
    final LayoutParams params =
        (FrameLayout.LayoutParams) innerView.getLayoutParams();

    if (pageNumber % 2 == 0) {
      params.leftMargin = viewPaddingLarge;
      params.rightMargin = viewPaddingSmall;
    } else {
      params.leftMargin = viewPaddingSmall;
      params.rightMargin = viewPaddingLarge;
    }

    // this calls requestLayout
    innerView.setLayoutParams(params);
  }

  public void showError(@StringRes int errorRes) {
    if (errorLayout == null) {
      inflateErrorLayout();
    }
    errorLayout.setVisibility(VISIBLE);
    errorText.setText(errorRes);
  }

  private void inflateErrorLayout() {
    final LayoutInflater inflater = LayoutInflater.from(context);
    errorLayout = inflater.inflate(R.layout.page_load_error, this, false);
    LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    lp.gravity = Gravity.CENTER;
    addView(errorLayout, lp);
    errorText = (TextView) errorLayout.findViewById(R.id.reason_text);
    final Button button =
        (Button) errorLayout.findViewById(R.id.retry_button);
    updateErrorTextColor();
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        errorLayout.setVisibility(GONE);
        if (pageController != null) {
          pageController.handleRetryClicked();
        }
      }
    });
  }

  private void updateErrorTextColor() {
    errorText.setTextColor(isNightMode ? Color.WHITE : Color.BLACK);
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
      pageController.onScrollChanged(x, y, oldx, oldy);
    }
  }
}
