package com.quran.labs.androidquran.widgets;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.util.QuranScreenInfo;

/*
 * Adapted and modified from http://code.google.com/p/android-page-curl
 * 
 * Modifications to take 3 pages at a time for previous, current, and next pages
 * Use SoftReference for pages
 * 
 * TODO: enable RTL flipping as a parameter
 * 
 * TODO: How do I pass on click events to my "children"? They're not really my children,
 * 		 I'm just holding them as my children anyways but they're really orphan views 
 * 		 with no root ViewGroup. I'm not giving them their rights because i'm 
 * 		 stealing touch from them. May Allah forgive me (4:10). Should be more like
 * 		 this: (4:6) ie. take when needed to help and give them their rights
 * 		
 * 		 Update: resolved by calling dispatchTouchEvent() on child. But still, children
 * 		 do not show focus or appear to the user that they have been clicked although they have!
 * 		 Guess I'm still failing on some parental rights.
 * 
 *       Update: DONE!
 * 
 * TODO: Problem when doing a "perfect" touch click (ie. ACTION_DOWN followed by ACTION_UP with
 * 		 no ACTION_MOVE in between). Page flip direction not determined and causes problems
 * 
 * 		Update: Fixed it (I think). But still in review
 */
public class QuranPageCurlView extends View {

	/** Log tag */
	private final static String TAG = "QuranPageCurlView";

	public interface OnPageFlipListener {

		public static final int PREVIOUS_PAGE = 1;
		public static final int NEXT_PAGE = 2;

		/*
		 * Will be called before page Flipping begins
		 */
		public void onPageFlipBegin(QuranPageCurlView pageView,
				final int flipDirection);

		/*
		 * Will be called when page Flipping ends
		 */
		public void onPageFlipEnd(QuranPageCurlView pageView,
				final int flipDirection);

	}

	// Debug text paint stuff
	private Paint mTextPaint;
	private TextPaint mTextPaintShadow;

	/** Px / Draw call */
	private int mCurlSpeed;

	/** Fixed update time used to create a smooth curl animation */
	private int mUpdateRate;

	/** The initial offset for x and y axis movements */
	private int mInitialEdgeOffset;

	/** The mode we will use */
	private int mCurlMode;
	
	/** The page background color */
	private int mBackgroundColor;

	/** Simple curl mode. Curl target will move only in one axis. */
	public static final int CURLMODE_SIMPLE = 0;

	/** Dynamic curl mode. Curl target will move on both X and Y axis. */
	public static final int CURLMODE_DYNAMIC = 1;

	/** Enable/Disable debug mode */
	private boolean bEnableDebugMode = false;

	/** The context which owns us */
	private WeakReference<Context> mContext;

	/** Handler used to auto flip time based */
	private FlipAnimationHandler mAnimationHandler;
	private ScrollAnimationHandler mScrollAnimationHandler;

	/** The finger position */
	private Vector2D mFinger;

	/** Page curl edge */
	private Paint mCurlEdgePaint, mEmptyPagePaint;

	/** If false no draw call has been done */
	private boolean bViewDrawn;

	/** Defines the flip direction that is currently considered */
	private boolean bFlipRight;

	/** If TRUE we are currently auto-flipping */
	private boolean bFlipping;

	/** TRUE if the user moves the pages */
	@SuppressWarnings("unused")
	private boolean bUserMoves;

	/** Used to control touch input blocking */
	private boolean bBlockTouchInput = false;

	/** Enable input after the next draw event */
	private boolean bEnableInputAfterDraw = false;

	/** Current page number */
	@SuppressWarnings("unused")
	private int mIndex = 0;

	/*
	 * Must hold minimum 3 items: current(shown),next(shown), previous Works
	 * well with book anyways because a book has minimum 3 pages (front cover, 1
	 * page, back cover)
	 * 
	 * Something to ponder about: Is 4th page needed (next-next page) if
	 * creating bitmap takes too long??
	 */

	/*
	 * Is page flipping allowed
	 */
	private boolean bAllowFlip = false;
	
	/*
	 * When last ACTION_DOWN event was received what was the page flip direction 
	 */
	private boolean bFlipRightOnDown = false;
	
	/*
	 * When last ACTION_MOVE event was received what was the page flip direction
	 */
	private boolean bFlipRightOnLastMove = false;

	/*
	 * Page Flip Listener
	 */
	private OnPageFlipListener mFlipListener;

	/*
	 * Drawing Views for Curling
	 */
	View mForegroundView;
	View mBackgroundView;

	/*
	 * Keep Hard references to certain needed pages to prolong their life so GC
	 * doesn't kill them (5:32)
	 */
	private View mPreviousPageView = null;
	private View mCurrentPageView = null;
	private View mNextPageView = null;

	// Offset of translated page
	private Vector2D mPageOffset;
	
	// Previous finger coordinates
	private Vector2D mPreviousFinger;
	
	// Width of the clip edge of the translated page
	private static final float clipOffset = 3;
	
	/* 
	 * Has a decision been taken on who to give touch events to
	 * in this current touch cycle
	 */
	private boolean dispatchDecisionTaken = false;
	
	/*
	 * Has the dispatch decision just been taken now?
	 */
	private boolean dispatchDecisionTakenNow = false;
	
	/*
	 * Will I be giving touch events to me?
	 */
	private boolean dispatchTouchToMe = false;
	
	/*
	 * When the last ACTION_DOWN was received what was the finger position
	 */
	private Vector2D mPositionOnDown;
	
	/*
	 * Distance after which I need to take a dispatch decision
	 * 
	 * Also used to account for fingers having slight movement even if a "perfect"
	 * click was intended by the user(ie. ACTION_DOWN followed immediately by ACTION_UP) 
	 * innama al a3malu bilniyyat...
	 * 
	 */
	private static final int FINGER_MOVEMENT_SLOP_DISTANCE = ViewConfiguration.getTouchSlop();
	
	/*
	 * Max time between ACTION_DOWN and ACTION_UP to consider this a click
	 * (completely heuristics)
	 */
	private static final int FINGER_CLICK_TIME_MAX = (int) (ViewConfiguration.getTapTimeout() * 1.5); //in milliseconds 
	

	/**
	 * Inner class used to represent a 2D point.
	 */
	private class Vector2D {
		public float x, y;

		public Vector2D(float x, float y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public String toString() {
			return "(" + this.x + "," + this.y + ")";
		}

		@SuppressWarnings("unused")
		public float length() {
			return (float) Math.sqrt(x * x + y * y);
		}

		@SuppressWarnings("unused")
		public float lengthSquared() {
			return (x * x) + (y * y);
		}

		public boolean equals(Object o) {
			if (o instanceof Vector2D) {
				Vector2D p = (Vector2D) o;
				return p.x == x && p.y == y;
			}
			return false;
		}

		@SuppressWarnings("unused")
		public Vector2D reverse() {
			return new Vector2D(-x, -y);
		}

		@SuppressWarnings("unused")
		public Vector2D sum(Vector2D b) {
			return new Vector2D(x + b.x, y + b.y);
		}

		@SuppressWarnings("unused")
		public Vector2D sub(Vector2D b) {
			return new Vector2D(x - b.x, y - b.y);
		}

		@SuppressWarnings("unused")
		public float dot(Vector2D vec) {
			return (x * vec.x) + (y * vec.y);
		}

		@SuppressWarnings("unused")
		public float cross(Vector2D a, Vector2D b) {
			return a.cross(b);
		}

		public float cross(Vector2D vec) {
			return x * vec.y - y * vec.x;
		}

		public float distanceSquared(Vector2D other) {
			float dx = other.x - x;
			float dy = other.y - y;

			return (dx * dx) + (dy * dy);
		}

		public float distance(Vector2D other) {
			return (float) Math.sqrt(distanceSquared(other));
		}

		public float dotProduct(Vector2D other) {
			return other.x * x + other.y * y;
		}

		@SuppressWarnings("unused")
		public Vector2D normalize() {
			float magnitude = (float) Math.sqrt(dotProduct(this));
			return new Vector2D(x / magnitude, y / magnitude);
		}

		@SuppressWarnings("unused")
		public Vector2D mult(float scalar) {
			return new Vector2D(x * scalar, y * scalar);
		}
	}

	/**
	 * Inner class used to make a fixed timed animation of the curl effect.
	 */
	class FlipAnimationHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			QuranPageCurlView.this.FlipAnimationStep();
		}

		public void sleep(long millis) {
			this.removeMessages(0);
			sendMessageDelayed(obtainMessage(0), millis);
		}
	}
	
	class ScrollAnimationHandler extends Handler {
		
		int mCount = 0;
		long updateRate = 0;
		
		@Override
		public void handleMessage(Message msg) {
			//Log.d(TAG, "animating scroll");
			QuranPageCurlView.this.invalidate();
			mCount--;
			this.sleep();
		}

		public void sleep() {
			this.removeMessages(0);
			if (mCount > 0) {
				sendMessageDelayed(obtainMessage(0), updateRate);
			}
		}
		
		public void setCount(int count){
			mCount = count;
		}
		
		public void setUpdateRate(long millis){
			updateRate = millis;
		}
	}

	/**
	 * Base
	 * 
	 * @param context
	 */
	public QuranPageCurlView(Context context) {
		super(context);
		init(context);
		ResetClipEdge();
	}

	/**
	 * Construct the object from an XML file. Valid Attributes:
	 * 
	 * @see android.view.View#View(android.content.Context,
	 *      android.util.AttributeSet)
	 */
	public QuranPageCurlView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);

		// Get the data from the XML AttributeSet
		{
			TypedArray a = context.obtainStyledAttributes(attrs,
					R.styleable.QuranPageCurlView);

			// Get data
			bEnableDebugMode = a.getBoolean(R.styleable.QuranPageCurlView_enableDebugMode, bEnableDebugMode);
			mCurlSpeed = a.getInt(R.styleable.QuranPageCurlView_curlSpeed, mCurlSpeed);
			mUpdateRate = a.getInt(R.styleable.QuranPageCurlView_updateRate, mUpdateRate);
			mInitialEdgeOffset = a.getInt(R.styleable.QuranPageCurlView_initialEdgeOffset, mInitialEdgeOffset);
			mCurlMode = a.getInt(R.styleable.QuranPageCurlView_curlMode, mCurlMode);
			mBackgroundColor = a.getColor(R.styleable.QuranPageCurlView_backgroundColor, mBackgroundColor);
			mCurlEdgePaint.setColor(mBackgroundColor);

			Log.i(TAG, "mCurlSpeed: " + mCurlSpeed);
			Log.i(TAG, "mUpdateRate: " + mUpdateRate);
			Log.i(TAG, "mInitialEdgeOffset: " + mInitialEdgeOffset);
			Log.i(TAG, "mCurlMode: " + mCurlMode);
			Log.i(TAG, "mBackgroundColor: " + mBackgroundColor);

			// recycle object (so it can be used by others)
			a.recycle();
		}

		ResetClipEdge();
	}

	public QuranPageCurlView(Context context, AttributeSet attrs, int defStyle) {
		this(context, attrs);
	}

	/**
	 * Initialize the view
	 */
	private final void init(Context context) {
		try {
			// clipPath is only available for software acceleration, so disable
			// hw acceleration on honeycomb and ics.
			Method setLayerMethod = View.class.getMethod(
			        "setLayerType", new Class[] { Integer.TYPE, Paint.class } );
			if (setLayerMethod != null){
				// 1 means View.LAYER_TYPE_SOFTWARE
				setLayerMethod.invoke(this, 1, null);
			}
			android.util.Log.d(TAG, "successfully set layer type to software");
		}
		catch (Exception e) {
			// pre-honeycomb/ics, so don't do anything
		}

		
		
		// Foreground text paint
		mTextPaint = new Paint();
		mTextPaint.setAntiAlias(true);
		mTextPaint.setTextSize(16);
		mTextPaint.setColor(0xFF000000);

		// The shadow
		mTextPaintShadow = new TextPaint();
		mTextPaintShadow.setAntiAlias(true);
		mTextPaintShadow.setTextSize(16);
		mTextPaintShadow.setColor(0x00000000);

		// Cache the context
		mContext = new WeakReference<Context>(context);

		// Base padding
		setPadding(3, 3, 3, 3);

		// The focus flags are needed
		setFocusable(true);
		setFocusableInTouchMode(true);

		mFinger = new Vector2D(0, 0);
		mPageOffset = new Vector2D(0, 0);
		mPreviousFinger = new Vector2D(0, 0);
		mPositionOnDown = new Vector2D(0, 0);

		// Create our curl animation handler
		mAnimationHandler = new FlipAnimationHandler();
		mScrollAnimationHandler = new ScrollAnimationHandler();
		
		mBackgroundColor = Color.WHITE;

		// Create our edge paint
		mCurlEdgePaint = new Paint();
		mCurlEdgePaint.setColor(mBackgroundColor);
		mCurlEdgePaint.setAntiAlias(true);
		mCurlEdgePaint.setStyle(Paint.Style.FILL);
		mCurlEdgePaint.setShadowLayer(5, -5, 5, 0x99000000);

		// Create empty page paint
		mEmptyPagePaint = new Paint();
		mEmptyPagePaint.setColor(Color.GRAY);
		mEmptyPagePaint.setAntiAlias(true);
		mEmptyPagePaint.setStyle(Paint.Style.FILL);

		// Set the default props, those come from an XML :D
		mCurlSpeed = 30;
		mUpdateRate = 33;
		mInitialEdgeOffset = 20;
		mCurlMode = 1;
		

		Log.d(TAG, "touch slop: " + FINGER_MOVEMENT_SLOP_DISTANCE + " time slop: " + FINGER_CLICK_TIME_MAX);
	}

	public void setOnPageFlipListener(OnPageFlipListener listener) {
		mFlipListener = listener;
	}

	/*
	 * Before displaying the view, need to layout and measure page according to
	 * our current View's layout specs
	 */
	private void pageViewMeasureAndLayout(View v) {
		if (v != null) {
			v.measure(MeasureSpec.makeMeasureSpec(getWidth(),
					MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
					getHeight(), MeasureSpec.EXACTLY));
			v.layout(0, 0, getWidth(), getHeight());
		}
	}

	/*
	 * Add new next page (if flipping forward)
	 */
	public void addNextPage(View v) {
		// Shift pages to make room for next page
		mPreviousPageView = mCurrentPageView;
		mCurrentPageView = mNextPageView;
		mNextPageView = v;
	}

	/*
	 * Add new previous page (if flipping backwards)
	 */
	public void addPreviousPage(View v) {
		// Shift pages to make room for previous page
		mNextPageView = mCurrentPageView;
		mCurrentPageView = mPreviousPageView;
		mPreviousPageView = v;
	}

	/*
	 * Set current page
	 */
	public void addCurrentPage(View v) {
		mCurrentPageView = v;
	}
	
	public View getCurrentPage() {
		return mCurrentPageView;
	}
	
	public void scrollPage(int scrollerId, int direction) {
		ScrollView sv = (ScrollView) mCurrentPageView.findViewById(scrollerId);
		if (sv != null){
			sv.arrowScroll(direction);
			mScrollAnimationHandler.setUpdateRate(mUpdateRate);
			mScrollAnimationHandler.setCount(20); // 33ms x 20 = ~0.75 second
			mScrollAnimationHandler.sleep();
		}
	}

	public void scrollToAyah(int scrollerId, AyahBounds yBounds) {
		ScrollView sv = (ScrollView) mCurrentPageView.findViewById(scrollerId);
		if (sv == null || yBounds == null)
			return;
		
		int screenHeight = QuranScreenInfo.getInstance().getHeight();
		int curScrollY = sv.getScrollY();
		int scrollToY = curScrollY;
		
		// If Ayah is within bounds, do nothing
		if (yBounds.getMinY() > curScrollY && yBounds.getMaxY() < curScrollY + screenHeight)
			return;
		
		int ayahHeight = yBounds.getMaxY() - yBounds.getMinY();
		
		// If entire ayah can fit in screen, center it vertically. Otherwise, scroll to top of Ayah.
		if (ayahHeight < screenHeight)
			scrollToY = yBounds.getMinY() - (screenHeight - ayahHeight)/2;
		else
			scrollToY = yBounds.getMinY() - (int) (screenHeight*0.05); // Leave a gap of 5% screen height
		
		sv.smoothScrollTo(sv.getScrollX(), scrollToY);
		mScrollAnimationHandler.setUpdateRate(mUpdateRate);
		mScrollAnimationHandler.setCount(20);
		mScrollAnimationHandler.sleep();
	}
	
	/**
	 * Reset points to it's initial clip edge state
	 */
	public void ResetClipEdge() {
		// Set our base movement
		mPreviousFinger.x = 0;

		
		// Add some offset to push it off the edge so it is not seen 
		mPageOffset.x = getWidth() + 30; 
	}

	/**
	 * Return the context which created use. Can return null if the context has
	 * been erased.
	 */
	@SuppressWarnings("unused")
	private Context GetContext() {
		return mContext.get();
	}

	/**
	 * See if the current curl mode is dynamic
	 */
	public boolean IsCurlModeDynamic() {
		return mCurlMode == CURLMODE_DYNAMIC;
	}

	/**
	 * Set the curl speed in px/frame
	 */
	public void SetCurlSpeed(int curlSpeed) {
		if (curlSpeed < 1)
			throw new IllegalArgumentException(
					"curlSpeed must be greated than 0");
		mCurlSpeed = curlSpeed;
	}

	/**
	 * Get the current curl speed in px/frame
	 */
	public int GetCurlSpeed() {
		return mCurlSpeed;
	}

	/**
	 * Set the update rate for the curl animation
	 */
	public void SetUpdateRate(int updateRate) {
		if (updateRate < 1)
			throw new IllegalArgumentException(
					"updateRate must be greated than 0");
		mUpdateRate = updateRate;
	}

	/**
	 * Get the current animation update rate in fps
	 */
	public int GetUpdateRate() {
		return mUpdateRate;
	}

	/**
	 * Set the initial pixel offset for the curl edge
	 */
	public void SetInitialEdgeOffset(int initialEdgeOffset) {
		if (initialEdgeOffset < 0)
			throw new IllegalArgumentException(
					"initialEdgeOffset can not negative");
		mInitialEdgeOffset = initialEdgeOffset;
	}

	/**
	 * Get the initial pixel offset for the curl edge
	 * 
	 * @return int - px
	 */
	public int GetInitialEdgeOffset() {
		return mInitialEdgeOffset;
	}

	/**
	 * Set the curl mode.
	 */
	public void SetCurlMode(int curlMode) {
		if (curlMode != CURLMODE_SIMPLE && curlMode != CURLMODE_DYNAMIC)
			throw new IllegalArgumentException("Invalid curlMode");
		mCurlMode = curlMode;
	}

	/**
	 * Return an integer that represents the current curl mode.
	 */
	public int GetCurlMode() {
		return mCurlMode;
	}

	/**
	 * Enable debug mode. This will draw a lot of data in the view so you can
	 * track what is happening
	 * 
	 * @param bFlag
	 *            - boolean flag
	 */
	public void SetEnableDebugMode(boolean bFlag) {
		bEnableDebugMode = bFlag;
	}

	/**
	 * Check if we are currently in debug mode.
	 * 
	 * @return boolean - If TRUE debug mode is on, FALSE otherwise.
	 */
	public boolean IsDebugModeEnabled() {
		return bEnableDebugMode;
	}

	/**
	 * @see android.view.View#measure(int, int)
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int finalWidth, finalHeight;
		finalWidth = measureWidth(widthMeasureSpec);
		finalHeight = measureHeight(heightMeasureSpec);
		setMeasuredDimension(finalWidth, finalHeight);
	}

	/**
	 * Determines the width of this view
	 * 
	 * @param measureSpec
	 *            A measureSpec packed into an int
	 * @return The width of the view, honoring constraints from measureSpec
	 */
	private int measureWidth(int measureSpec) {
		int result = 0;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			result = ((View) getParent()).getWidth();
		}

		return result;
	}

	/**
	 * Determines the height of this view
	 * 
	 * @param measureSpec
	 *            A measureSpec packed into an int
	 * @return The height of the view, honoring constraints from measureSpec
	 */
	private int measureHeight(int measureSpec) {
		int result = 0;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else {
			result = ((View) getParent()).getHeight();
		}
		return result;
	}
	
	public void doPageFlip(int direction) {
		
		switch(direction){
			case OnPageFlipListener.PREVIOUS_PAGE:
				bFlipRight = bFlipRightOnDown = bFlipRightOnLastMove = true;
				mPageOffset.x = getWidth();
				break;
			case OnPageFlipListener.NEXT_PAGE:
				bFlipRight = bFlipRightOnDown = bFlipRightOnLastMove = false;
				// set up canvas for next page
				nextDrawView();

				// Start from far left
				mPageOffset.x = 1;
				break;
		}
		
		bUserMoves = false;
		bFlipping = true;
		if (mFlipListener != null  && (bFlipRightOnDown == bFlipRightOnLastMove)) {
			mFlipListener.onPageFlipBegin(this,
					bFlipRight ? OnPageFlipListener.PREVIOUS_PAGE : OnPageFlipListener.NEXT_PAGE);
		}
		FlipAnimationStep();
	}

	/**
	 * Determine whether or not to dispatch the touch event to myself to draw or
	 * to my immediate child (eg. button pres, scrolling etc)
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		//Log.d(TAG, "\n\ndispatching..... " + event.getAction());
		if (mCurrentPageView != null) {
			boolean hasScroller = ((Boolean) mCurrentPageView.getTag()).booleanValue();
			
			/*
			// If there is no scroll view then always dispatch events to me
			if (!hasScroller) {
				return super.dispatchTouchEvent(event);
			}
			*/

			/**
			 * Strategy explained:
			 * 
			 * First, give to both me and children, then when distance goes
			 * beyond a certain point, then we find out if movement is
			 * vertical or horizontal oriented. I use this distance to make
			 * a final decision on who takes the remaining touches
			 */
			
			mFinger.x = event.getX();
			mFinger.y = event.getY();
			
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				// record first down touch
				mPositionOnDown.x = event.getX();
				mPositionOnDown.y = event.getY();
				
				dispatchDecisionTaken = false;
				dispatchTouchToMe = false;
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
				//Reset positions (do i need this?)
				mPositionOnDown.x = 0;
				mPositionOnDown.y = 0;
				
				// animate scroll if my child??
				if (!dispatchDecisionTaken || (dispatchDecisionTaken && !dispatchTouchToMe)){
					mScrollAnimationHandler.setUpdateRate(mUpdateRate);
					mScrollAnimationHandler.setCount(30); // 33ms x 30 = ~1 second
					mScrollAnimationHandler.sleep();
				}
				
				break;
			case MotionEvent.ACTION_MOVE:
				// as we move calculate distance and make final decision
				if (!dispatchDecisionTaken && (mFinger.distance(mPositionOnDown) > FINGER_MOVEMENT_SLOP_DISTANCE)){
					
					dispatchDecisionTaken = true;
					dispatchDecisionTakenNow = true;
					
					final float xMovement = Math.abs(mFinger.x - mPositionOnDown.x);
					final float yMovement = Math.abs(mFinger.y - mPositionOnDown.y);
					
					// Horizontal movement mainly => page flip
					if (xMovement >= yMovement){
						dispatchTouchToMe = true;
					} else { //Vertical movement mainly => scrolling
						// but only assign to child if it has a scroller
						dispatchTouchToMe = hasScroller? false : true;
					}
				}
				break;
			}
			
			//Log.d(TAG, "On Down Pos: " + mPositionOnDown);
			//Log.d(TAG, "my finger: " + mFinger);
			//Log.d(TAG, "decisionTaken: " + dispatchDecisionTaken);
			//Log.d(TAG, "taken now: " + dispatchDecisionTakenNow);
			//Log.d(TAG, "to me: " + dispatchTouchToMe);

			// Let's see what the decisions were
			if (dispatchDecisionTaken){
				if (dispatchTouchToMe){
					final boolean consumed = super.dispatchTouchEvent(event);
					/*
					 * Send cancel to my children first 
					 */
					if (dispatchDecisionTakenNow) {
						dispatchDecisionTakenNow = false;
						event.setAction(MotionEvent.ACTION_CANCEL);
						mCurrentPageView.dispatchTouchEvent(event);
					}
					
					return consumed;
				} else {
					final boolean childConsumed = mCurrentPageView.dispatchTouchEvent(event);
					
					// Need to redraw screen to show updated children if they changed
					if (childConsumed)
						invalidate();
	
					/*
					 * Send cancel to myself first 
					 */
					if (dispatchDecisionTakenNow) {
						dispatchDecisionTakenNow = false;
						event.setAction(MotionEvent.ACTION_CANCEL);
						super.dispatchTouchEvent(event);
					}
					
					return childConsumed;
				}
			} else {
				// Both of us take the touch event until decision is taken
				final boolean childConsumed = mCurrentPageView.dispatchTouchEvent(event);
				return (super.dispatchTouchEvent(event) || childConsumed);
			}
		}
			
		Log.d(TAG, "my current view is null!");
		// current view is null so I always get it
		return super.dispatchTouchEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		//Log.d(TAG, "dispatched to me");
		
		int width = getWidth();
		
		/* If this was a click, then fire onClickListener and cancel current page flip because
		 * it seems that this was not intended for page flip.
		 * 
		 * Click is defined as:
		 *   - down/up where time is smaller than FINGER_CLICK_TIME_MAX
		 *   - down/up where distance between down and up is smaller than FINGER_MOVEMENT_SLOP_DISTANCE
		 *     (if dispatchDecisionTaken is true, then we've moved too far already)
		 */
		if ((event.getAction() == MotionEvent.ACTION_UP) &&
				((event.getEventTime() - event.getDownTime()) <= FINGER_CLICK_TIME_MAX) &&
				!dispatchDecisionTaken) {
			//float xPos = event.getX();
			
			/* 
			 * Only perform click if the tap was in the center of the screen. 
			 * Otherwise, just perform the ACTION_UP as normal which should 
			 * flip the page depending which side of the screen the tap was on
			 * 
			 * update 9/4/2011 - removed this behavior because it was confusing
			 * and made it always click on touch.
			 */
			//if ((xPos > width/4) && (xPos < width*3/4)){
				event.setAction(MotionEvent.ACTION_CANCEL);
				performClick();
			//}
		}
		
		
		// Blocking touch due to a page flip animation that is ongoing
		if (!bBlockTouchInput) {

			// Get our finger position
			mFinger.x = event.getX();
			mFinger.y = event.getY();

			// Depending on the action do what we need to
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				mPreviousFinger.x = mFinger.x;

				bAllowFlip = false;

				// If we moved over the half of the display flip to next
				if (mPreviousFinger.x > (width >> 1)) {
					// Start from far right
					mPageOffset.x = getWidth();
					

					// Set the right movement flag
					bFlipRight = true;
					bFlipRightOnDown = true;
					bFlipRightOnLastMove = true;

					if (mPreviousPageView != null) {
						bAllowFlip = true;
					}
				} else {
					// Set the left movement flag
					bFlipRight = false;
					bFlipRightOnDown = false;
					bFlipRightOnLastMove = false;

					if (mNextPageView != null) {
						bAllowFlip = true;

						// set up canvas for next page
						nextDrawView();

						// Start from far left
						mPageOffset.x = 1;
					}
				}

				break;

			case MotionEvent.ACTION_UP:
				if (bAllowFlip) {
					bUserMoves = false;
					bFlipping = true;
					if (mFlipListener != null  && (bFlipRightOnDown == bFlipRightOnLastMove)) {
						mFlipListener.onPageFlipBegin(this,
								bFlipRight ? OnPageFlipListener.PREVIOUS_PAGE : OnPageFlipListener.NEXT_PAGE);
					}
					FlipAnimationStep();
				}
				break;
			case MotionEvent.ACTION_CANCEL:
				if (bAllowFlip) {
					bUserMoves = false;
					bFlipping = true;
					bFlipRightOnLastMove = !bFlipRightOnDown; // make sure we dont call pageflip listener
				
					// return back to original page
					if (bFlipRightOnDown)
						bFlipRight = false; 
					else
						bFlipRight = true;
				
					FlipAnimationStep();
				}
				break;
			case MotionEvent.ACTION_MOVE:
				if (bAllowFlip) {
					bUserMoves = true;

					// calculate new position
					mPageOffset.x += (mFinger.x - mPreviousFinger.x);

					// Cap movement to page limits
					mPageOffset.x = Math.min(getWidth() - 1, mPageOffset.x);
					mPageOffset.x = Math.max(0, mPageOffset.x);

					/* 
					 * Make sure that we've moved enough beyond the finger slop distance
					 * or else a long finger click can cause strange behavior because
					 * the direction will be incorrectly determined
					 */
					if (dispatchDecisionTaken){
						
						// Get movement direction
						if (mFinger.x < mPreviousFinger.x) {
							bFlipRight = true;
							bFlipRightOnLastMove = true;
						} else {
							bFlipRight = false;
							bFlipRightOnLastMove = false;
						}
					}

					// Store finger postion for next round
					mPreviousFinger.x = mFinger.x;

					// Force a new draw call
					this.invalidate();
				}

				break;
			}

		}

		/*
		 * TODO: Only consume event if we need to? Probably better to change wallahu
		 * a3lam but it's not critical inshaAllah
		 */
		return true;
	}

	/**
	 * Execute a step of the flip animation
	 */
	public void FlipAnimationStep() {
		if (!bFlipping)
			return;

		int width = getWidth();

		// No input when flipping
		bBlockTouchInput = true;

		// Handle speed
		float curlSpeed = mCurlSpeed;
		if (!bFlipRight)
			curlSpeed *= -1;

		// Move page offset
		mPageOffset.x -= curlSpeed;

		// Check for endings :D
		if (mPageOffset.x < 0 || mPageOffset.x > width) {
			bFlipping = false;

			// Call listener to let them know flipping is done
			if (mFlipListener != null && (bFlipRightOnDown == bFlipRightOnLastMove)) {
				mFlipListener.onPageFlipEnd(this, 
						bFlipRight ? OnPageFlipListener.PREVIOUS_PAGE : OnPageFlipListener.NEXT_PAGE);
			}

			if (bFlipRight) {
				previousDrawView();
			}

			ResetClipEdge();

			// Enable touch input after the next draw event
			bEnableInputAfterDraw = true;
		} else {
			// Cap movement to page limits
			mPageOffset.x = Math.min(getWidth() - 1, mPageOffset.x);
			mPageOffset.x = Math.max(0, mPageOffset.x);

			mAnimationHandler.sleep(mUpdateRate);
		}

		// Force a new draw call
		this.invalidate();
	}

	public void nextDrawView() {
		mForegroundView = mNextPageView;
		mBackgroundView = mCurrentPageView; // new next?
	}

	public void previousDrawView() {
		mForegroundView = mCurrentPageView;
		mBackgroundView = mPreviousPageView;
	}

	// ---------------------------------------------------------------
	// Drawing methods
	// ---------------------------------------------------------------

	@Override
	protected void onDraw(Canvas canvas) {

		// We need to initialize all size data when we first draw the view
		if (!bViewDrawn) {
			bViewDrawn = true;
			onFirstDrawEvent(canvas);
		}

		canvas.drawColor(Color.WHITE);

		Rect rect = new Rect();
		rect.left = 0;
		rect.top = 0;
		rect.right = getWidth();
		rect.bottom = getHeight();

		// First Page render
		Paint paint = new Paint();

		// Draw our elements
		drawForegroundView(canvas, rect, paint);
		drawCurlEdge(canvas);
		drawBackgroundView(canvas, rect, paint);

		// Check if we can re-enable input
		if (bEnableInputAfterDraw) {
			bBlockTouchInput = false;
			bEnableInputAfterDraw = false;
		}

	}

	/**
	 * Called on the first draw event of the view
	 * 
	 * @param canvas
	 */
	protected void onFirstDrawEvent(Canvas canvas) {
		ResetClipEdge();
	}

	/**
	 * Create a Path used as a mask to draw the background page
	 * 
	 * @return
	 */
	private Path createForegroundPath() {

		Path path = new Path();
		path.moveTo(0, 0);
		path.lineTo(mPageOffset.x, 0);
		path.lineTo(mPageOffset.x, getHeight());
		path.lineTo(0, getHeight());
		path.lineTo(0, 0);
		return path;
	}

	/**
	 * Draw the foreground
	 * 
	 * @param canvas
	 * @param rect
	 * @param paint
	 */
	private void drawForegroundView(Canvas canvas, Rect rect, Paint paint) {

		Path mask = createForegroundPath();

		// Save current canvas so we do not mess it up
		canvas.save();
		canvas.clipPath(mask);

		if (mForegroundView != null) {
			pageViewMeasureAndLayout(mForegroundView);
			mForegroundView.draw(canvas);
		} else {
			canvas.drawRect(0, 0, getWidth(), getHeight(), mEmptyPagePaint);
		}

		canvas.restore();
	}

	/**
	 * Draw the background image.
	 * 
	 * @param canvas
	 * @param rect
	 * @param paint
	 */
	private void drawBackgroundView(Canvas canvas, Rect rect, Paint paint) {
		// Save current canvas so we do not mess it up
		canvas.save();
		canvas.translate(mPageOffset.x, 0);

		// canvas.clipPath(mask);

		if (mBackgroundView != null) {
			pageViewMeasureAndLayout(mBackgroundView);
			mBackgroundView.draw(canvas);
		} else {
			canvas.drawRect(0, 0, getWidth(), getHeight(), mEmptyPagePaint);
		}

		canvas.restore();
	}

	/**
	 * Creates a path used to draw the curl edge in.
	 * 
	 * @return
	 */
	private Path createCurlEdgePath() {
		Path path = new Path();
		path.moveTo(mPageOffset.x, 0);
		path.lineTo(mPageOffset.x + clipOffset, 0);
		path.lineTo(mPageOffset.x + clipOffset, getHeight());
		path.lineTo(mPageOffset.x, getHeight());
		path.lineTo(mPageOffset.x, 0);
		return path;
	}

	/**
	 * Draw the curl page edge
	 * 
	 * @param canvas
	 */
	private void drawCurlEdge(Canvas canvas) {
		Path path = createCurlEdgePath();
		canvas.drawPath(path, mCurlEdgePaint);
	}

	// ---------------------------------------------------------------
	// Debug draw methods
	// ---------------------------------------------------------------

	/**
	 * Draw a text with a nice shadow
	 */
	public static void drawTextShadowed(Canvas canvas, String text, float x,
			float y, Paint textPain, Paint shadowPaint) {
		canvas.drawText(text, x - 1, y, shadowPaint);
		canvas.drawText(text, x, y + 1, shadowPaint);
		canvas.drawText(text, x + 1, y, shadowPaint);
		canvas.drawText(text, x, y - 1, shadowPaint);
		canvas.drawText(text, x, y, textPain);
	}

	/**
	 * Draw a text with a nice shadow centered in the X axis
	 * 
	 * @param canvas
	 * @param text
	 * @param y
	 * @param textPain
	 * @param shadowPaint
	 */
	public static void drawCentered(Canvas canvas, String text, float y,
			Paint textPain, Paint shadowPaint) {
		float posx = (canvas.getWidth() - textPain.measureText(text)) / 2;
		drawTextShadowed(canvas, text, posx, y, textPain, shadowPaint);
	}
	
	public void refresh(boolean initialSetup){
		if (initialSetup){
			previousDrawView();
		}

		this.invalidate();
	}

	public void refresh() {
		refresh(false);
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		// TODO Auto-generated method stub
		super.onRestoreInstanceState(state);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		// TODO Auto-generated method stub
		return super.onSaveInstanceState();
	}
	
	

}