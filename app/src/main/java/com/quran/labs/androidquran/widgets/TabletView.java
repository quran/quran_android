package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.support.annotation.IntDef;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.quran.labs.androidquran.ui.util.PageController;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class TabletView extends LinearLayout {
  public static final int QURAN_PAGE = 1;
  public static final int TRANSLATION_PAGE = 2;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef( { QURAN_PAGE, TRANSLATION_PAGE } )
  public @interface TabletPageType {}

  private Context context;
  private QuranPageLayout leftPage;
  private QuranPageLayout rightPage;

  public TabletView(Context context) {
    super(context);
    this.context = context;
    setOrientation(HORIZONTAL);
  }

  public void init(@TabletPageType int leftPageType, @TabletPageType int rightPageType) {
    leftPage = getPageLayout(leftPageType);
    rightPage = getPageLayout(rightPageType);

    final LayoutParams leftParams = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
    leftParams.weight = 1;
    addView(leftPage, leftParams);

    final LayoutParams rightParams = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
    rightParams.weight = 1;
    addView(rightPage, rightParams);
  }

  private QuranPageLayout getPageLayout(@TabletPageType int type) {
    switch (type) {
      case TRANSLATION_PAGE: {
        return new QuranTranslationPageLayout(context);
      }
      case QURAN_PAGE:
      default: {
        return new QuranTabletImagePageLayout(context);
      }
    }
  }

  public void setPageController(PageController controller, int leftPage, int rightPage) {
    this.leftPage.setPageController(controller, leftPage);
    this.rightPage.setPageController(controller, rightPage);
  }

  public void updateView(boolean nightMode, boolean useNewBackground) {
    leftPage.updateView(nightMode, useNewBackground, 2);
    rightPage.updateView(nightMode, useNewBackground, 2);
  }

  public QuranPageLayout getLeftPage() {
    return leftPage;
  }

  public QuranPageLayout getRightPage() {
    return rightPage;
  }
}
