package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.task.QueryAyahCoordsTask;
import com.quran.labs.androidquran.task.QueryPageCoordsTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
import com.quran.labs.androidquran.ui.helpers.PageDownloadListener;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.QuranPageWorker;
import com.quran.labs.androidquran.ui.util.ImageAyahUtils;
import com.quran.labs.androidquran.ui.util.PageController;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.util.QuranUtils;
import com.quran.labs.androidquran.widgets.AyahToolBar;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.QuranImagePageLayout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableCompletableObserver;
import io.reactivex.observers.DisposableObserver;
import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class QuranPageFragment extends Fragment
    implements AyahTracker, PageController, PageDownloadListener {
  private static final String PAGE_NUMBER_EXTRA = "pageNumber";

  private int pageNumber;
  private AsyncTask currentTask;
  private Map<String, List<AyahBounds>> coordinatesData;

  private AyahSelectedListener ayahSelectedListener;

  private boolean overlayText;
  private boolean justCreated;
  private Future<?> pageLoadTask;

  @Inject BookmarkModel bookmarkModel;
  @Inject QuranPageWorker quranPageWorker;

  private HighlightingImageView imageView;
  private QuranImagePageLayout quranPageLayout;
  private CompositeDisposable compositeDisposable;
  private Handler handler = new Handler();

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
    pageNumber = getArguments() != null ? getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
    setHasOptionsMenu(true);
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!justCreated) {
      updateView();
    }
    justCreated = false;
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final Context context = getActivity();
    quranPageLayout = new QuranImagePageLayout(context);
    quranPageLayout.setPageController(this, pageNumber);
    imageView = quranPageLayout.getImageView();

    if (coordinatesData != null) {
      imageView.setCoordinateData(coordinatesData);
    }
    updateView();

    justCreated = true;
    return quranPageLayout;
  }

  @Override
  public void updateView() {
    Context context = getActivity();
    if (context == null || !isAdded()) {
      return;
    }

    final QuranSettings settings = QuranSettings.getInstance(context);
    final boolean useNewBackground = settings.useNewBackground();
    final boolean isNightMode = settings.isNightMode();
    overlayText = settings.shouldOverlayPageInfo();
    quranPageLayout.updateView(isNightMode, useNewBackground, 1);
    if (!settings.highlightBookmarks()) {
      imageView.unHighlight(HighlightType.BOOKMARK);
    }
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) context.getApplicationContext()).getApplicationComponent().inject(this);
    if (context instanceof AyahSelectedListener) {
      ayahSelectedListener = (AyahSelectedListener) context;
    }
    compositeDisposable = new CompositeDisposable();
  }

  @Override
  public void onDetach() {
    ayahSelectedListener = null;
    compositeDisposable.dispose();
    super.onDetach();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    Activity activity = getActivity();
    if (activity instanceof PagerActivity) {
      final PagerActivity pagerActivity = (PagerActivity) activity;

      downloadImage();

      handler.postDelayed(() ->
          new QueryPageCoordinatesTask(pagerActivity).execute(pageNumber), 1000);

      if (QuranSettings.getInstance(activity).shouldHighlightBookmarks()) {
        // Observable.timer by default runs on Schedulers.computation()
        compositeDisposable.add(Completable.timer(250, TimeUnit.MILLISECONDS)
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableCompletableObserver() {
              @Override
              public void onComplete() {
                highlightTagsTask();
              }

              @Override
              public void onError(Throwable e) {
              }
            }));
      }
    }
  }

  private void downloadImage() {
    if (isAdded()) {
      pageLoadTask = quranPageWorker.loadPage(
          QuranScreenInfo.getInstance().getWidthParam(),
          pageNumber, QuranPageFragment.this);
    }
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    pageLoadTask = null;
    if (quranPageLayout == null || !isAdded()) {
      return;
    }

    if (drawable != null) {
      imageView.setImageDrawable(drawable);
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
      quranPageLayout.showError(errorRes);
      quranPageLayout.setOnClickListener(v -> {
        if (ayahSelectedListener != null) {
          ayahSelectedListener.onClick(EventType.SINGLE_TAP);
        }
      });
    }
  }

  @Override
  public void onDestroyView() {
    if (currentTask != null) {
      currentTask.cancel(true);
    }
    currentTask = null;
    super.onDestroyView();
  }

  public void cleanup() {
    Timber.d("cleaning up page %d", pageNumber);
    handler.removeCallbacksAndMessages(null);
    if (pageLoadTask != null) {
      pageLoadTask.cancel(false);
    }

    if (quranPageLayout != null) {
      imageView.setImageDrawable(null);
      quranPageLayout = null;
    }
  }

  private void highlightTagsTask() {
    compositeDisposable.add(
        bookmarkModel.getBookmarkedAyahsOnPageObservable(pageNumber)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<List<Bookmark>>() {
              @Override
              public void onNext(List<Bookmark> bookmarks) {
                for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
                  Bookmark taggedAyah = bookmarks.get(i);
                  imageView.highlightAyah(taggedAyah.sura,
                      taggedAyah.ayah, HighlightType.BOOKMARK);
                }

                if (coordinatesData == null) {
                  if (currentTask != null &&
                      !(currentTask instanceof QueryAyahCoordsTask)) {
                    currentTask.cancel(true);
                    currentTask = null;
                  }

                  if (currentTask == null) {
                    currentTask = new GetAyahCoordsTask(
                        getActivity()).execute(pageNumber);
                  }
                } else {
                  imageView.invalidate();
                }
              }

              @Override
              public void onError(Throwable e) {
              }

              @Override
              public void onComplete() {
              }
            }));
  }

  private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
    QueryPageCoordinatesTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getWidthParam());
    }

    @Override
    protected void onPostExecute(RectF[] rect) {
      if (rect != null && rect.length == 1 && isAdded()) {
        imageView.setPageBounds(rect[0]);
        if (overlayText) {
          int page = pageNumber;
          Context context = getContext();
          String suraText = QuranInfo.getSuraNameFromPage(context, page, true);
          String juzText = QuranInfo.getJuzString(context, page);
          String pageText = QuranUtils.getLocalizedNumber(context, page);
          String rub3Text = QuranDisplayHelper.displayRub3(context,page);
          imageView.setOverlayText(suraText, juzText, pageText, rub3Text);
        }
      }
    }
  }

  private class GetAyahCoordsTask extends QueryAyahCoordsTask {
    GetAyahCoordsTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getWidthParam());
    }

    GetAyahCoordsTask(Context context, MotionEvent event, EventType eventType) {
      super(context, event, eventType,
          QuranScreenInfo.getInstance().getWidthParam(), pageNumber);
    }

    GetAyahCoordsTask(Context context, int sura, int ayah,
                      HighlightType type) {
      super(context, QuranScreenInfo.getInstance().getWidthParam(),
          sura, ayah, type);
    }

    @Override
    protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps) {
      if (isAdded()) {
        if (maps != null && maps.size() > 0) {
          coordinatesData = maps.get(0);
          imageView.setCoordinateData(coordinatesData);
        }

        if (mHighlightAyah) {
          handleHighlightAyah(mSura, mAyah, mHighlightType, true);
        } else if (mEvent != null) {
          handlePress(mEvent, mEventType);
        } else {
          imageView.invalidate();
        }
      }
      currentTask = null;
    }
  }

  @Override
  public void highlightAyat(
      int page, Set<String> ayahKeys, HighlightType type) {
    if (page == pageNumber && quranPageLayout != null) {
      imageView.highlightAyat(ayahKeys, type);
      imageView.invalidate();
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah,
      HighlightType type, boolean scrollToAyah) {
    if (coordinatesData == null) {
      if (currentTask != null &&
          !(currentTask instanceof QueryAyahCoordsTask)) {
        currentTask.cancel(true);
        currentTask = null;
      }

      if (currentTask == null) {
        currentTask = new GetAyahCoordsTask(
            getActivity(), sura, ayah, type).execute(pageNumber);
      }
    } else {
      handleHighlightAyah(sura, ayah, type, scrollToAyah);
    }
  }

  private void handleHighlightAyah(int sura, int ayah,
      HighlightType type, boolean scrollToAyah) {
    imageView.highlightAyah(sura, ayah, type);
    if (scrollToAyah && quranPageLayout.canScroll()) {
      final RectF highlightBounds = ImageAyahUtils.
          getYBoundsForHighlight(coordinatesData, sura, ayah);
      if (highlightBounds != null) {
        int screenHeight = QuranScreenInfo.getInstance().getHeight();

        Matrix matrix = imageView.getImageMatrix();
        matrix.mapRect(highlightBounds);

        int currentScrollY = quranPageLayout.getCurrentScrollY();
        final boolean topOnScreen = highlightBounds.top > currentScrollY &&
            highlightBounds.top < currentScrollY + screenHeight;
        final boolean bottomOnScreen = highlightBounds.bottom > currentScrollY &&
            highlightBounds.bottom < currentScrollY + screenHeight;

        if (!topOnScreen || !bottomOnScreen) {
          int y = (int) highlightBounds.top - (int) (0.05 * screenHeight);
          quranPageLayout.smoothScrollLayoutTo(y);
        }
      }
    }
    imageView.invalidate();
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, type, true);
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight) {
    final List<AyahBounds> bounds = coordinatesData == null ? null :
        coordinatesData.get(sura + ":" + ayah);
    final int screenWidth = imageView == null ? 0 : imageView.getWidth();
    if (bounds != null && screenWidth > 0) {
      final int screenHeight = QuranScreenInfo.getInstance().getHeight();
      AyahToolBar.AyahToolBarPosition position =
          ImageAyahUtils.getToolBarPosition(bounds,
              imageView.getImageMatrix(), screenWidth, screenHeight,
              toolBarWidth, toolBarHeight);
      // If we're in landscape mode (wrapped in SV) update the y-offset
      position.yScroll = 0 - quranPageLayout.getCurrentScrollY();
      return position;
    }
    return null;
  }

  @Override
  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    imageView.unHighlight(sura, ayah, type);
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    imageView.unHighlight(type);
  }

  private void handlePress(MotionEvent event, EventType eventType) {
    QuranAyah result = ImageAyahUtils.getAyahFromCoordinates(
        coordinatesData, imageView, event.getX(), event.getY());
    if (result != null && ayahSelectedListener != null) {
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      ayahSelectedListener.onAyahSelected(eventType, suraAyah, this);
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
    if (coordinatesData == null) {
      currentTask = new GetAyahCoordsTask(getActivity(),
          event, eventType).execute(pageNumber);
      return false;
    }
    // All good
    return true;
  }

  @Override
  public void handleRetryClicked() {
    quranPageLayout.setOnClickListener(null);
    quranPageLayout.setClickable(false);
    downloadImage();
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event,
      EventType eventType, int page) {
    if (eventType == EventType.DOUBLE_TAP) {
      unHighlightAyahs(HighlightType.SELECTION);
    }

    if (ayahSelectedListener == null) {
      return false;
    }

    if (ayahSelectedListener.isListeningForAyahSelection(eventType)) {
      if (checkCoordinateData(event, eventType)) {
        handlePress(event, eventType);
      }
      return true;
    } else {
      return ayahSelectedListener.onClick(eventType);
    }
  }
}
