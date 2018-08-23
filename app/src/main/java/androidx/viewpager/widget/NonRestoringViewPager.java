package androidx.viewpager.widget;

import android.content.Context;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;

/**
 * NonRestoringViewPager is a hack to sometimes prevent ViewPager from restoring its
 * page in onRestoreInstanceState. This is done because in some cases, the ViewPager
 * has a different number of pages in portrait and landscape, and so restoring the
 * old page number is incorrect (and requires making 3 fragments that will be thrown
 * away shortly thereafter).
 *
 * It also contains the same swallowing of an Exception that is occasionally thrown
 * on certain devices in onTouchEvent (this hack is also present in QuranViewPager).
 *
 * Note that the package name for this is a hack to allow overriding setCurrentItemInternal,
 * which is package protected, so as to be able to ignore the initial "restore page" event
 * without ignoring the entirety of saving and restoring of state.
 *
 * Hopefully, this is a short term solution. Note that an alternative solution that also
 * works is to handle our own SavedState here (and call super during save, but ignore the
 * result, and call super during restore with null). This, however, seemed a bit cleaner
 * for now, since we still want to get save and restore on the PagerAdapter.
 */
public class NonRestoringViewPager extends ViewPager {
  private boolean isRestoring = false;
  private boolean useDefaultImplementation;

  public NonRestoringViewPager(Context context) {
    super(context);
  }

  public NonRestoringViewPager(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public void setIsDualPagesInLandscape(boolean isDualPagesInLandscape) {
    useDefaultImplementation = !isDualPagesInLandscape;
  }

  @Override
  void setCurrentItemInternal(int item, boolean smoothScroll, boolean always) {
    if (useDefaultImplementation || !isRestoring) {
      super.setCurrentItemInternal(item, smoothScroll, always);
    }
  }

  @Override
  void setCurrentItemInternal(int item, boolean smoothScroll, boolean always, int velocity) {
    if (useDefaultImplementation || !isRestoring) {
      super.setCurrentItemInternal(item, smoothScroll, always, velocity);
    }
  }

  @Override
  public void onRestoreInstanceState(Parcelable state) {
    isRestoring = true;
    super.onRestoreInstanceState(state);
    isRestoring = false;
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev) {
    try {
      return super.onTouchEvent(ev);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
