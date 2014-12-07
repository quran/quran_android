package com.quran.labs.androidquran.ui.util;

import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.ui.helpers.QuranListAdapter;
import com.quran.labs.androidquran.ui.helpers.QuranRow;

import android.content.Context;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * This class handles touch for the RecyclerView. It is heavily based
 * on ClickItemTouchListener from twoway-view, with some changes:
 * - properly handle long pressing on an element that isn't long pressable and
 *   then scrolling (in this case, we should clear selection and not click,
 *   whereas the original code would click).
 * - removal of onScroll override (due to the above handling scrolling).
 *
 * ClickItemTouchListener is part of twoway-view:
 * https://github.com/lucasr/twoway-view/
 * at: src/main/java/org/lucasr/twowayview/ClickItemTouchListener.java
 */
public class QuranListTouchListener implements
    RecyclerView.OnItemTouchListener {
  private Context mContext;
  private RecyclerView mRecyclerView;
  private QuranListAdapter mListAdapter;
  private GestureDetectorCompat mGestureDetector;
  private View mTargetView;
  private int mTouchSlop;
  private float mOriginalY;

  public QuranListTouchListener(Context context, RecyclerView recyclerView) {
    mContext = context;
    mRecyclerView = recyclerView;
    mListAdapter = (QuranListAdapter) mRecyclerView.getAdapter();
    mGestureDetector = new TouchGestureDetector(
        context, new TouchGestureListener());
    mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
  }

  private class TouchGestureDetector extends GestureDetectorCompat {
    private GestureDetector.OnGestureListener mGestureListener;

    public TouchGestureDetector(Context context,
        GestureDetector.OnGestureListener listener) {
      super(context, listener);
      mGestureListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      final boolean handled = super.onTouchEvent(event);

      if (mTargetView != null) {
        final int action = event.getAction();
        if (action == MotionEvent.ACTION_UP) {
          // happens on long press without scrolling when long
          // press is not handling the event.
          mGestureListener.onSingleTapUp(event);
        } else if (action == MotionEvent.ACTION_MOVE) {
          // if we scroll or move more than touch slop, cancel
          if (Math.abs(mOriginalY - event.getY()) >= mTouchSlop) {
            mTargetView.setPressed(false);
            mTargetView = null;
          }
        }
      }
      return handled;
    }
  }

  private class TouchGestureListener extends
      GestureDetector.SimpleOnGestureListener {
    @Override
    public boolean onSingleTapUp(MotionEvent e) {
      if (mTargetView != null) {
        mTargetView.setPressed(false);
        final int position = mRecyclerView.getChildPosition(mTargetView);

        mTargetView = null;
        if (position >= 0 && mListAdapter.isEnabled(position)) {
          onClick(mListAdapter.getQuranRow(position), position);
          return true;
        }
      }
      return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
      if (mTargetView != null) {
        final int position = mRecyclerView.getChildPosition(mTargetView);
        if (position >= 0 &&
            onLongClick(mListAdapter.getQuranRow(position), position)) {
          mTargetView.setPressed(false);
          mTargetView = null;
        }
      }
    }

    @Override
    public boolean onDown(MotionEvent e) {
      mTargetView = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
      mOriginalY = e.getY();
      return mTargetView != null;
    }

    @Override
    public void onShowPress(MotionEvent e) {
      if (mTargetView != null) {
        mTargetView.setPressed(true);
      }
    }
  }

  @Override
  public boolean onInterceptTouchEvent(RecyclerView recyclerView,
      MotionEvent motionEvent) {
    mGestureDetector.onTouchEvent(motionEvent);
    return false;
  }

  @Override
  public void onTouchEvent(RecyclerView recyclerView, MotionEvent event) {
  }

  public void onClick(QuranRow row, int position) {
    if (row.page > 0) {
      ((QuranActivity) mContext).jumpTo(row.page);
    }
  }

  public boolean onLongClick(QuranRow row, int position) {
    return false;
  }
}
