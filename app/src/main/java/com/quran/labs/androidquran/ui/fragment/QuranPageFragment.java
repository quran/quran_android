package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.task.QueryAyahCoordsTask;
import com.quran.labs.androidquran.task.QueryBookmarkedAyahsTask;
import com.quran.labs.androidquran.task.QueryPageCoordsTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.ObservableScrollView;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class QuranPageFragment extends Fragment
    implements AyahTracker, ObservableScrollView.OnScrollListener {

  private static final String TAG = "QuranPageFragment";
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private int mPageNumber;
  private AsyncTask mCurrentTask;
  private HighlightingImageView mImageView;
  private ObservableScrollView mScrollView;
  private PaintDrawable mLeftGradient, mRightGradient = null;

  private AyahSelectedListener mAyahSelectedListener;

  private boolean mOverlayText;
  private boolean mJustCreated;

  private View mMainView;
  private View mErrorLayout;
  private TextView mErrorReason;
  private ImageView mLeftBorder, mRightBorder;
  private Map<String, List<AyahBounds>> mCoordinateData;

  private Resources mResources;
  private SharedPreferences mPrefs;
  private Handler mHandler = new Handler();

  public static QuranPageFragment newInstance(int page) {
    final QuranPageFragment f = new QuranPageFragment();
    final Bundle args = new Bundle();
    args.putInt(PAGE_NUMBER_EXTRA, page);
    f.setArguments(args);
    return f;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mPageNumber = getArguments() != null ?
        getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
    Display display = getActivity().getWindowManager().getDefaultDisplay();
    int width = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT ?
            QuranDisplayHelper.getWidthKitKat(display) : display.getWidth();
    mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
    mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
    setHasOptionsMenu(true);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!mJustCreated) {
      updateView();
    }
    mJustCreated = false;
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.quran_page_layout,
        container, false);

    mResources = getResources();

    mLeftBorder = (ImageView) view.findViewById(R.id.left_border);
    mRightBorder = (ImageView) view.findViewById(R.id.right_border);

    mImageView = (HighlightingImageView) view.findViewById(R.id.page_image);
    mScrollView = (ObservableScrollView) view.findViewById(R.id.page_scroller);

    mMainView = view;
    mErrorLayout = view.findViewById(R.id.error_layout);
    final Button retryButton = (Button) view.findViewById(R.id.retry_button);
    mErrorReason = (TextView) view.findViewById(R.id.reason_text);
    retryButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mErrorLayout.setVisibility(View.GONE);
        mMainView.setOnClickListener(null);
        mMainView.setClickable(false);
        downloadImage();
      }
    });

    mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    updateView();

    final GestureDetector gestureDetector = new GestureDetector(
        new PageGestureDetector());
    OnTouchListener gestureListener = new OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
      }
    };
    mImageView.setOnTouchListener(gestureListener);
    mImageView.setClickable(true);
    mImageView.setLongClickable(true);

    if (mScrollView != null) {
      mScrollView.setOnScrollListener(this);
    }

    mOverlayText = mPrefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);

    if (mCoordinateData != null) {
      mImageView.setCoordinateData(mCoordinateData);
    }

    mJustCreated = true;
    return view;
  }

  private View.OnClickListener viewClickListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      if (mAyahSelectedListener != null) {
        mAyahSelectedListener.onClick(EventType.SINGLE_TAP);
      }
    }
  };

  public void updateView() {
    int lineImageId = R.drawable.dark_line;
    int leftBorderImageId = R.drawable.border_left;
    int rightBorderImageId = R.drawable.border_right;

    Context context = getActivity();
    if (context == null || mResources == null ||
        mMainView == null || !isAdded()) {
      return;
    }

    mMainView.setBackgroundDrawable((mPageNumber % 2 == 0 ?
        mLeftGradient : mRightGradient));
    if (!mPrefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true)) {
      mMainView.setBackgroundColor(mResources.getColor(
          R.color.page_background));
    }

    boolean nightMode = false;
    int nightModeTextBrightness =
        Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
    if (mPrefs.getBoolean(Constants.PREF_NIGHT_MODE, false)) {
      leftBorderImageId = R.drawable.night_left_border;
      rightBorderImageId = R.drawable.night_right_border;
      lineImageId = R.drawable.light_line;
      mMainView.setBackgroundColor(Color.BLACK);
      nightMode = true;
      nightModeTextBrightness =
          QuranSettings.getNightModeTextBrightness(context);
      mErrorReason.setTextColor(Color.WHITE);
    } else {
      mErrorReason.setTextColor(Color.BLACK);
    }

    if (mPageNumber % 2 == 0) {
      mRightBorder.setVisibility(View.GONE);
      mLeftBorder.setBackgroundResource(leftBorderImageId);
    } else {
      mRightBorder.setVisibility(View.VISIBLE);
      mRightBorder.setBackgroundResource(rightBorderImageId);
      mLeftBorder.setBackgroundResource(lineImageId);
    }

    mImageView.setNightMode(nightMode, nightModeTextBrightness);

    if (!mPrefs.getBoolean(Constants.PREF_HIGHLIGHT_BOOKMARKS, true)) {
      mImageView.unHighlight(HighlightType.BOOKMARK);
    }
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    if (activity instanceof AyahSelectedListener) {
      mAyahSelectedListener = (AyahSelectedListener) activity;
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mAyahSelectedListener = null;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    Activity activity = getActivity();
    if (PagerActivity.class.isInstance(activity)) {
      final PagerActivity pagerActivity = (PagerActivity)activity;

      mHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          downloadImage();
        }
      }, 250);

      mHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
          new QueryPageCoordinatesTask(pagerActivity).execute(mPageNumber);
        }
      }, 1000);

      if (QuranSettings.shouldHighlightBookmarks(pagerActivity)) {
        mHandler.postDelayed(new Runnable() {
          @Override
          public void run() {
            new HighlightTagsTask(pagerActivity).execute(mPageNumber);
          }
        }, 250);
      }
    }
  }

  private void downloadImage() {
    final Activity activity = getActivity();
    if (activity instanceof PagerActivity) {
      QuranPageWorker worker = ((PagerActivity) activity).getQuranPageWorker();
      worker.loadPage(QuranScreenInfo.getInstance().getWidthParam(),
          mPageNumber, QuranPageFragment.this);
    }
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    if (mImageView == null || mErrorLayout == null) {
      return;
    }

    if (drawable != null) {
      mImageView.setImageDrawable(drawable);
      // TODO we should toast a warning if we couldn't save the image
      // (which would likely happen if we can't write to the sdcard,
      // but just got the page from the web).
    } else if (response != null) {
      // failed to get the image... let's notify the user
      mErrorLayout.setVisibility(View.VISIBLE);
      final int errorCode = response.getErrorCode();
      final int errorRes;
      switch (errorCode) {
        case Response.ERROR_SD_CARD_NOT_FOUND:
          errorRes = R.string.sdcard_error;
          break;
        case Response.ERROR_DOWNLOADING_ERROR:
          errorRes = R.string.download_error_network;
          break;
        default:
          errorRes = R.string.download_error_general;
      }
      mErrorReason.setText(errorRes);
      mMainView.setOnClickListener(viewClickListener);
    }
  }

  @Override
  public void onDestroyView() {
    if (mCurrentTask != null) {
      mCurrentTask.cancel(true);
    }
    mCurrentTask = null;
    super.onDestroyView();
  }

  public void cleanup() {
    android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
    mHandler.removeCallbacksAndMessages(null);
    if (mImageView != null) {
      mImageView.setImageDrawable(null);
      mImageView = null;
    }

  }

  private class HighlightTagsTask extends QueryBookmarkedAyahsTask {

    public HighlightTagsTask(Context context) {
      super(context);
    }

    @Override
    protected void onPostExecute(List<BookmarksDBAdapter.Bookmark> result) {
      if (result != null && !result.isEmpty() && mImageView != null) {
        for (BookmarksDBAdapter.Bookmark taggedAyah : result) {
          mImageView.highlightAyah(taggedAyah.mSura, taggedAyah.mAyah, HighlightType.BOOKMARK);
        }
        if (mCoordinateData == null) {
          if (mCurrentTask != null &&
              !(mCurrentTask instanceof QueryAyahCoordsTask)) {
            mCurrentTask.cancel(true);
            mCurrentTask = null;
          }

          if (mCurrentTask == null) {
            mCurrentTask = new GetAyahCoordsTask(getActivity()).execute(mPageNumber);
          }
        } else {
          mImageView.invalidate();
        }
      }
    }
  }

  private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
    public QueryPageCoordinatesTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getWidthParam());
    }

    @Override
    protected void onPostExecute(Rect[] rect) {
      if (rect != null && rect.length == 1) {
        if (mImageView != null) {
          mImageView.setPageBounds(rect[0]);
          if (mOverlayText) {
            mImageView.setOverlayText(mPageNumber, true);
          }
        }
      }
    }
  }

  private class GetAyahCoordsTask extends QueryAyahCoordsTask {

    private boolean mScrollToAyah = true;

    public GetAyahCoordsTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getWidthParam());
    }

    public GetAyahCoordsTask(Context context, MotionEvent event, EventType eventType) {
      super(context, event, eventType,
          QuranScreenInfo.getInstance().getWidthParam(), mPageNumber);
    }

    public GetAyahCoordsTask(Context context, int sura, int ayah,
                             HighlightType type, boolean scrollToAyah) {
      super(context, QuranScreenInfo.getInstance().getWidthParam(),
          sura, ayah, type);
      mScrollToAyah = scrollToAyah;
    }

    @Override
    protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps) {
      if (mImageView == null) {
        return;
      }

      if (maps != null && maps.size() > 0) {
        mCoordinateData = maps.get(0);
        mImageView.setCoordinateData(mCoordinateData);
      }

      if (mHighlightAyah) {
        handleHighlightAyah(mSura, mAyah, mHighlightType, mScrollToAyah);
      } else if (mEvent != null) {
        handlePress(mEvent, mEventType);
      } else {
        mImageView.invalidate();
      }
      mCurrentTask = null;
    }
  }

  @Override
  public void highlightAyat(
      int page, Set<String> ayahKeys, HighlightType type) {
    if (page == mPageNumber && mImageView != null) {
      mImageView.highlightAyat(ayahKeys, type);
      mImageView.invalidate();
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, type, true);
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (mCoordinateData == null) {
      if (mCurrentTask != null &&
          !(mCurrentTask instanceof QueryAyahCoordsTask)) {
        mCurrentTask.cancel(true);
        mCurrentTask = null;
      }

      if (mCurrentTask == null) {
        mCurrentTask = new GetAyahCoordsTask(
            getActivity(), sura, ayah, type, scrollToAyah).execute(mPageNumber);
      }
    } else {
      handleHighlightAyah(sura, ayah, type, scrollToAyah);
    }
  }

  private void handleHighlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (mImageView == null) {
      return;
    }
    mImageView.highlightAyah(sura, ayah, type);
    if (mScrollView != null && scrollToAyah) {
      AyahBounds yBounds = ImageAyahUtils.
          getYBoundsForHighlight(mCoordinateData, sura, ayah);
      if (yBounds != null) {
        int screenHeight = QuranScreenInfo.getInstance().getHeight();
        int y = yBounds.getMinY() - (int) (0.05 * screenHeight);
        mScrollView.smoothScrollTo(mScrollView.getScrollX(), y);
      }
    }
    mImageView.invalidate();
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight) {
    final List<AyahBounds> bounds = mCoordinateData == null ? null :
        mCoordinateData.get(sura + ":" + ayah);
    final int screenWidth = mImageView == null ? 0 : mImageView.getWidth();
    if (bounds != null && screenWidth > 0) {
      final int screenHeight = QuranScreenInfo.getInstance().getHeight();
      AyahToolBar.AyahToolBarPosition position =
          ImageAyahUtils.getToolBarPosition(bounds, screenWidth,
              screenHeight, toolBarWidth, toolBarHeight);
      // If we're in landscape mode (wrapped in SV) update the y-offset
      if (mScrollView != null) {
        position.yScroll = 0 - mScrollView.getScrollY();
      }
      return position;
    }
    return null;
  }

  @Override
  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    mImageView.unHighlight(sura, ayah, type);
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    mImageView.unHighlight(type);
  }

  private void handlePress(MotionEvent event, EventType eventType) {
    QuranAyah result = ImageAyahUtils.getAyahFromCoordinates(
        mCoordinateData, mImageView, event.getX(), event.getY());
    if (result != null && mAyahSelectedListener != null) {
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      mAyahSelectedListener.onAyahSelected(eventType, suraAyah, this);
    }
  }

  @Override
  public void onScrollChanged(
      ObservableScrollView scrollView, int x, int y, int oldx, int oldy) {
    PagerActivity activity = (PagerActivity) getActivity();
    if (activity != null) {
      activity.onQuranPageScroll(y);
    }
  }

  private class PageGestureDetector extends SimpleOnGestureListener {
    @Override
    public boolean onDown(MotionEvent e) {
      return true;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
      return handleEvent(event, EventType.SINGLE_TAP);
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
      unHighlightAyahs(HighlightType.SELECTION);
      return handleEvent(event, EventType.DOUBLE_TAP);
    }

    @Override
    public void onLongPress(MotionEvent event) {
      handleEvent(event, EventType.LONG_PRESS);
    }

    private boolean checkCoordinateData(MotionEvent event, EventType eventType) {
      // Check files downloaded
      if (!QuranFileUtils.haveAyaPositionFile(getActivity()) ||
          !QuranFileUtils.hasArabicSearchDatabase(getActivity())) {
        Activity activity = getActivity();
        if (activity != null) {
          PagerActivity pagerActivity = (PagerActivity) activity;
          pagerActivity.showGetRequiredFilesDialog();
          return false;
        }
      }
      // Check we fetched the data
      if (mCoordinateData == null) {
        mCurrentTask = new GetAyahCoordsTask(getActivity(),
            event, eventType).execute(mPageNumber);
        return false;
      }
      // All good
      return true;
    }

    private boolean handleEvent(MotionEvent event, EventType eventType) {
      if (mAyahSelectedListener == null) return false;
      if (mAyahSelectedListener.isListeningForAyahSelection(eventType)) {
        if (checkCoordinateData(event, eventType)) {
          handlePress(event, eventType);
        }
        return true;
      } else {
        return mAyahSelectedListener.onClick(eventType);
      }
    }
  }

}
