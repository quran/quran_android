package com.quran.labs.androidquran.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Gallery;

import com.quran.labs.androidquran.R;

// http://stackoverflow.com/questions/5286115
public class QuranGallery extends Gallery {
	
	// Gallery Scrolling Speed
	public static final int GALLERY_SCROLLING_SPEED = 250;
	private static final int LANDSCAPE_SCROLLING_SPEED = 50;

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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
    	GalleryFriendlyScrollView scroller = null;
    	// Get the scroller from the layout - Only present in landscape mode
    	scroller = (GalleryFriendlyScrollView) findViewById(R.id.pageScrollView);
    	switch(keyCode) {
    	case KeyEvent.KEYCODE_DPAD_DOWN:
    		if (scroller != null) {
    			scroller.scrollBy(0, LANDSCAPE_SCROLLING_SPEED);
    		}
    		break;
    	case KeyEvent.KEYCODE_DPAD_UP:
    		if (scroller != null) {
    			scroller.scrollBy(0, -LANDSCAPE_SCROLLING_SPEED);
    		}
    		break;
    	case KeyEvent.KEYCODE_DPAD_LEFT:
    		break;
    	case KeyEvent.KEYCODE_DPAD_RIGHT:
    		break;
    	}
    	return super.onKeyDown(keyCode, event);
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
			velocityX = GALLERY_SCROLLING_SPEED;
		} else {
			velocityX = -GALLERY_SCROLLING_SPEED;
		}

    	return super.onFling(e1, e2, velocityX, velocityY);
    }
    
	private boolean isScrollingLeft(MotionEvent e1, MotionEvent e2) {
		return e2.getX() > e1.getX();
	}
}
