package com.quran.labs.androidquran.view;

import android.content.Context;
import android.view.View;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.page.common.data.PageMode;
import com.quran.page.common.factory.PageViewFactory;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class TabletView extends QuranPageWrapperLayout {
  public static final int QURAN_PAGE = 1;
  public static final int TRANSLATION_PAGE = 2;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef( { QURAN_PAGE, TRANSLATION_PAGE } )
  @interface TabletPageType {}

  private final Context context;
  private QuranPageLayout leftPage;
  private QuranPageLayout rightPage;
  private PageController pageController;

  public TabletView(Context context) {
    super(context);
    this.context = context;
  }

  public void init(
      @TabletPageType int leftPageType,
      @TabletPageType int rightPageType,
      @Nullable PageViewFactory pageViewFactory,
      int leftPageNumber,
      int rightPageNumber) {
    leftPage = getPageLayout(leftPageType, pageViewFactory, leftPageNumber);
    rightPage = getPageLayout(rightPageType, pageViewFactory, rightPageNumber);

    addView(leftPage);
    addView(rightPage);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int pageWidth = MeasureSpec.makeMeasureSpec(getMeasuredWidth() / 2, MeasureSpec.EXACTLY);
    int pageHeight = MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY);
    leftPage.measure(pageWidth, pageHeight);
    rightPage.measure(pageWidth, pageHeight);
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    final int width = getMeasuredWidth();
    final int height = getMeasuredHeight();
    leftPage.layout(0, 0, width / 2, height);
    rightPage.layout(width / 2, 0, width, height);
  }

  private QuranPageLayout getPageLayout(
      @TabletPageType int type,
      @Nullable PageViewFactory pageViewFactory,
      int page
  ) {
    if (type == TRANSLATION_PAGE) {
      return new QuranTranslationPageLayout(context);
    } else {
      if (pageViewFactory != null) {
        final View view = pageViewFactory.providePageView(context, page, PageMode.DualScreenMode.Arabic.INSTANCE);
        if (view != null) {
          return new QuranCustomImagePageLayout(context, view);
        }
      }
      return new QuranTabletImagePageLayout(context);
    }
  }

  public void setPageController(PageController controller, int leftPage, int rightPage, int skips) {
    this.pageController = controller;
    this.leftPage.setPageController(controller, leftPage, skips);
    this.rightPage.setPageController(controller, rightPage, skips);
  }

  public void setPageController(PageController controller, int pageNumber, int skips) {
    this.pageController = controller;

    this.rightPage.setPageController(controller, pageNumber, skips);
    this.leftPage.setPageController(controller, pageNumber, skips);
  }

  @Override
  public void updateView(@NonNull QuranSettings quranSettings) {
    super.updateView(quranSettings);
    leftPage.updateView(quranSettings);
    rightPage.updateView(quranSettings);
  }

  @Override
  public void showError(@StringRes int errorRes) {
    super.showError(errorRes);
    rightPage.shouldHideLine = true;
    rightPage.invalidate();
  }

  @Override
  public void hideError() {
    super.hideError();
    rightPage.shouldHideLine = false;
    rightPage.invalidate();
  }

  public QuranPageLayout getLeftPage() {
    return leftPage;
  }

  public QuranPageLayout getRightPage() {
    return rightPage;
  }

  @Override
  void handleRetryClicked() {
    if (pageController != null) {
      rightPage.shouldHideLine = false;
      rightPage.invalidate();
      pageController.handleRetryClicked();
    }
  }
}
