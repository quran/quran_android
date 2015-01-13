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
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.QuranImagePageLayout;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class QuranPageFragment extends Fragment
    implements AyahTracker, PageController {

  private static final String TAG = "QuranPageFragment";
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private int mPageNumber;
  private AsyncTask mCurrentTask;
  private Map<String, List<AyahBounds>> mCoordinatesData;

  private AyahSelectedListener mAyahSelectedListener;

  private boolean mOverlayText;
  private boolean mJustCreated;
  private Future<?> mPageLoadTask;

  private HighlightingImageView mImageView;
  private QuranImagePageLayout mQuranPageLayout;
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
    final Context context = getActivity();
    mQuranPageLayout = new QuranImagePageLayout(context);
    mQuranPageLayout.setPageController(this, mPageNumber);
    mImageView = mQuranPageLayout.getImageView();
    mPrefs = PreferenceManager.getDefaultSharedPreferences(context);

    if (mCoordinatesData != null) {
      mImageView.setCoordinateData(mCoordinatesData);
    }
    updateView();

    mJustCreated = true;
    return mQuranPageLayout;
  }

  @Override
  public void updateView() {
    Context context = getActivity();
    if (context == null || !isAdded()) {
      return;
    }

    final boolean useNewBackground =
        mPrefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true);
    final boolean isNightMode =
        mPrefs.getBoolean(Constants.PREF_NIGHT_MODE, false);
    mOverlayText =
        mPrefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);
    mQuranPageLayout.updateView(isNightMode, useNewBackground);
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
    mAyahSelectedListener = null;
    super.onDetach();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    Activity activity = getActivity();
    if (activity instanceof PagerActivity) {
      final PagerActivity pagerActivity = (PagerActivity) activity;

      mHandler.postDelayed(new Runnable() {
        @Override
        public void run() {
           downloadImage();
        }
      }, 150);

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
      mPageLoadTask = worker.loadPage(
          QuranScreenInfo.getInstance().getWidthParam(),
          mPageNumber, QuranPageFragment.this);
    }
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    mPageLoadTask = null;
    if (mQuranPageLayout == null || !isAdded()) {
      return;
    }

    if (drawable != null) {
      mImageView.setImageDrawable(drawable);
      // TODO we should toast a warning if we couldn't save the image
      // (which would likely happen if we can't write to the sdcard,
      // but just got the page from the web).
    } else if (response != null) {
      // failed to get the image... let's notify the user
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
      mQuranPageLayout.showError(errorRes);
      mQuranPageLayout.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (mAyahSelectedListener != null) {
            mAyahSelectedListener.onClick(EventType.SINGLE_TAP);
          }
        }
      });
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
    if (mPageLoadTask != null) {
      mPageLoadTask.cancel(false);
    }

    if (mQuranPageLayout != null) {
      mImageView.setImageDrawable(null);
      mQuranPageLayout = null;
    }
  }

  private class HighlightTagsTask extends QueryBookmarkedAyahsTask {

    public HighlightTagsTask(Context context) {
      super(context);
    }

    @Override
    protected void onPostExecute(List<BookmarksDBAdapter.Bookmark> result) {
      if (result != null && !result.isEmpty() && isAdded()) {
        for (BookmarksDBAdapter.Bookmark taggedAyah : result) {
          mImageView.highlightAyah(taggedAyah.mSura,
              taggedAyah.mAyah, HighlightType.BOOKMARK);
        }
        if (mCoordinatesData == null) {
          if (mCurrentTask != null &&
              !(mCurrentTask instanceof QueryAyahCoordsTask)) {
            mCurrentTask.cancel(true);
            mCurrentTask = null;
          }

          if (mCurrentTask == null) {
            mCurrentTask = new GetAyahCoordsTask(
                getActivity()).execute(mPageNumber);
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
    protected void onPostExecute(RectF[] rect) {
      if (rect != null && rect.length == 1 && isAdded()) {
        mImageView.setPageBounds(rect[0]);
        if (mOverlayText) {
          mImageView.setOverlayText(mPageNumber, true);
        }
      }
    }
  }

  private class GetAyahCoordsTask extends QueryAyahCoordsTask {
    public GetAyahCoordsTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getWidthParam());
    }

    public GetAyahCoordsTask(Context context, MotionEvent event, EventType eventType) {
      super(context, event, eventType,
          QuranScreenInfo.getInstance().getWidthParam(), mPageNumber);
    }

    public GetAyahCoordsTask(Context context, int sura, int ayah,
                             HighlightType type) {
      super(context, QuranScreenInfo.getInstance().getWidthParam(),
          sura, ayah, type);
    }

    @Override
    protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps) {
      if (isAdded()) {
        if (maps != null && maps.size() > 0) {
          mCoordinatesData = maps.get(0);
          mImageView.setCoordinateData(mCoordinatesData);
        }

        if (mHighlightAyah) {
          handleHighlightAyah(mSura, mAyah, mHighlightType, true);
        } else if (mEvent != null) {
          handlePress(mEvent, mEventType);
        } else {
          mImageView.invalidate();
        }
      }
      mCurrentTask = null;
    }
  }

  @Override
  public void highlightAyat(
      int page, Set<String> ayahKeys, HighlightType type) {
    if (page == mPageNumber && mQuranPageLayout != null) {
      mImageView.highlightAyat(ayahKeys, type);
      mImageView.invalidate();
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah,
      HighlightType type, boolean scrollToAyah) {
    if (mCoordinatesData == null) {
      if (mCurrentTask != null &&
          !(mCurrentTask instanceof QueryAyahCoordsTask)) {
        mCurrentTask.cancel(true);
        mCurrentTask = null;
      }

      if (mCurrentTask == null) {
        mCurrentTask = new GetAyahCoordsTask(
            getActivity(), sura, ayah, type).execute(mPageNumber);
      }
    } else {
      handleHighlightAyah(sura, ayah, type, scrollToAyah);
    }
  }

  private void handleHighlightAyah(int sura, int ayah,
      HighlightType type, boolean scrollToAyah) {
    mImageView.highlightAyah(sura, ayah, type);
    if (scrollToAyah && mQuranPageLayout.canScroll()) {
      final RectF highlightBounds = ImageAyahUtils.
          getYBoundsForHighlight(mCoordinatesData, sura, ayah);
      if (highlightBounds != null) {
        int screenHeight = QuranScreenInfo.getInstance().getHeight();
        int y = (int) highlightBounds.top - (int) (0.05 * screenHeight);
        mQuranPageLayout.smoothScrollLayoutTo(y);
      }
    }
    mImageView.invalidate();
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, type, true);
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight) {
    final List<AyahBounds> bounds = mCoordinatesData == null ? null :
        mCoordinatesData.get(sura + ":" + ayah);
    final int screenWidth = mImageView == null ? 0 : mImageView.getWidth();
    if (bounds != null && screenWidth > 0) {
      final int screenHeight = QuranScreenInfo.getInstance().getHeight();
      AyahToolBar.AyahToolBarPosition position =
          ImageAyahUtils.getToolBarPosition(bounds,
              mImageView.getImageMatrix(), screenWidth, screenHeight,
              toolBarWidth, toolBarHeight);
      // If we're in landscape mode (wrapped in SV) update the y-offset
      position.yScroll = 0 - mQuranPageLayout.getCurrentScrollY();
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
        mCoordinatesData, mImageView, event.getX(), event.getY());
    if (result != null && mAyahSelectedListener != null) {
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      mAyahSelectedListener.onAyahSelected(eventType, suraAyah, this);
    }
  }

  @Override
  public void onScrollChanged(int x, int y, int oldx, int oldy) {
    PagerActivity activity = (PagerActivity) getActivity();
    if (activity != null) {
      activity.onQuranPageScroll(y);
    }
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
    if (mCoordinatesData == null) {
      mCurrentTask = new GetAyahCoordsTask(getActivity(),
          event, eventType).execute(mPageNumber);
      return false;
    }
    // All good
    return true;
  }

  @Override
  public void handleRetryClicked() {
    mQuranPageLayout.setOnClickListener(null);
    mQuranPageLayout.setClickable(false);
    downloadImage();
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event,
      EventType eventType, int page) {
    if (eventType == EventType.DOUBLE_TAP) {
      unHighlightAyahs(HighlightType.SELECTION);
    }

    if (mAyahSelectedListener == null) {
      return false;
    }

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
