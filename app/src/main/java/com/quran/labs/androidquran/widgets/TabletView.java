package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.ui.util.PageController;

import android.content.Context;
import android.support.annotation.IntDef;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class TabletView extends LinearLayout {
  public static final int QURAN_PAGE = 1;
  public static final int TRANSLATION_PAGE = 2;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef( { QURAN_PAGE, TRANSLATION_PAGE } )
  public @interface TabletPageType {}

  private Context mContext;
  private QuranPageLayout mLeftPage;
  private QuranPageLayout mRightPage;

  public TabletView(Context context) {
    super(context);
    mContext = context;
    setOrientation(HORIZONTAL);
  }

  public void init(@TabletPageType int leftPageType,
      @TabletPageType int rightPageType) {
    mLeftPage = getPageLayout(leftPageType);
    mRightPage = getPageLayout(rightPageType);

    final LayoutParams leftParams = new LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT);
    leftParams.weight = 1;
    addView(mLeftPage, leftParams);

    final LayoutParams rightParams = new LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT);
    rightParams.weight = 1;
    addView(mRightPage, rightParams);
  }

  private QuranPageLayout getPageLayout(@TabletPageType int type) {
    switch (type) {
      case TRANSLATION_PAGE: {
        return new QuranTranslationPageLayout(mContext);
      }
      case QURAN_PAGE:
      default: {
        return new QuranTabletImagePageLayout(mContext);
      }
    }
  }

  public void setPageController(PageController controller,
      int leftPage, int rightPage) {
    mLeftPage.setPageController(controller, leftPage);
    mRightPage.setPageController(controller, rightPage);
  }

  public void updateView(boolean nightMode, boolean useNewBackground) {
    mLeftPage.updateView(nightMode, useNewBackground);
    mRightPage.updateView(nightMode, useNewBackground);
  }

  public QuranPageLayout getLeftPage() {
    return mLeftPage;
  }

  public QuranPageLayout getRightPage() {
    return mRightPage;
  }
}
