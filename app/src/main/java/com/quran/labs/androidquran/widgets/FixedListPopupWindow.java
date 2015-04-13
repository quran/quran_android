package com.quran.labs.androidquran.widgets;

import com.crashlytics.android.Crashlytics;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.ListPopupWindow;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;

/**
 * class taken from:
 * https://code.google.com/p/android/issues/detail?id=64402
 *
 * to work around android 2.x scrolling issues.
 */
public class FixedListPopupWindow extends ListPopupWindow {

  public FixedListPopupWindow(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
      Field anchor;
      try {
        anchor = ListPopupWindow.class.getDeclaredField("mPopup");
        anchor.setAccessible(true);
        PopupWindow window = (PopupWindow) anchor.get(this);
        fixPopupWindow(window);
      }
      catch (Exception e) {
        Crashlytics.logException(e);
      }
    }
  }

  private void fixPopupWindow(final PopupWindow window) {
    try {
      final Field anchor = PopupWindow.class.getDeclaredField("mAnchor");
      anchor.setAccessible(true);
      Field listener = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
      listener.setAccessible(true);
      final ViewTreeObserver.OnScrollChangedListener originalListener =
          (ViewTreeObserver.OnScrollChangedListener) listener.get(window);
      ViewTreeObserver.OnScrollChangedListener newListener =
          new ViewTreeObserver.OnScrollChangedListener() {
        @Override
        public void onScrollChanged() {
          try {
            WeakReference<?> mAnchor = (WeakReference<?>) anchor.get(window);
            if (mAnchor != null && mAnchor.get() != null) {
              originalListener.onScrollChanged();
            }
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          }
        }
      };
      listener.set(window, newListener);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
