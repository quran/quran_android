package com.quran.labs.androidquran.widgets;

import com.quran.labs.androidquran.data.ApplicationConstants;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Gallery;

// http://stackoverflow.com/questions/5286115
public class QuranGallery extends Gallery {

	private float mInitialX;
    private float mInitialY;
    private boolean mNeedToRebase;
    private boolean mIgnore;

    
	public QuranGallery(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
            float distanceY) {
        if (mNeedToRebase) {
            mNeedToRebase = false;
            distanceX = 0;
        }
        return super.onScroll(e1, e2, distanceX, distanceY);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                mIgnore = false;
                mNeedToRebase = true;
                mInitialX = e.getX();
                mInitialY = e.getY();
                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                if (!mIgnore) {
                    float deltaX = Math.abs(e.getX() - mInitialX);
                    float deltaY = Math.abs(e.getY() - mInitialY);
                    mIgnore = deltaX < deltaY;
                    return !mIgnore;
                }
                return false;
            }
            default: {
                return super.onInterceptTouchEvent(e);
            }
        }
    }
    
    /**
     * Checkout this post at stack overflow
     * http://stackoverflow.com/questions/2373617/how-to-stop-scrolling-in-a-gallery-widget
     */
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    	boolean leftScroll = false;
		if (e1 != null && e2 != null)
			leftScroll = isScrollingLeft(e1, e2);

		if (leftScroll) {
			velocityX = ApplicationConstants.GALLERY_SCROLLING_SPEED;
		} else {
			velocityX = -ApplicationConstants.GALLERY_SCROLLING_SPEED;
		}

    	return super.onFling(e1, e2, velocityX, velocityY);
    }
    
	private boolean isScrollingLeft(MotionEvent e1, MotionEvent e2) {
		return e2.getX() > e1.getX();
	}
}
