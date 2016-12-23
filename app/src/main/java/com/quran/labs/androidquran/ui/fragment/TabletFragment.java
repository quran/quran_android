package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.task.QueryAyahCoordsTask;
import com.quran.labs.androidquran.task.QueryPageCoordsTask;
import com.quran.labs.androidquran.task.TranslationTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahSelectedListener;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.HighlightType;
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
import com.quran.labs.androidquran.widgets.QuranTranslationPageLayout;
import com.quran.labs.androidquran.widgets.TabletView;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class TabletFragment extends Fragment
    implements AyahTracker, PageController {
  private static final String FIRST_PAGE_EXTRA = "pageNumber";
  private static final String MODE_EXTRA = "mode";

  public static class Mode {
    public static final int ARABIC = 1;
    public static final int TRANSLATION = 2;
    public static final int MIXED = 3;
  }

  private int mode;
  private int pageNumber;
  private boolean mOverlayText;
  private int lastHighlightedPage;
  private AyahSelectedListener ayahSelectedListener;
  private List<Map<String, List<AyahBounds>>> coordinateData;
  private TranslationView leftTranslation, rightTranslation = null;
  private HighlightingImageView leftImageView, rightImageView = null;
  private QuranSettings quranSettings;
  private CompositeDisposable compositeDisposable = new CompositeDisposable();

  private TabletView mainView;

  @Inject BookmarkModel bookmarkModel;

  private Future<?> leftPageLoadTask;
  private Future<?> rightPageLoadTask;

  private boolean justCreated;

  public static TabletFragment newInstance(int firstPage, int mode) {
    final TabletFragment f = new TabletFragment();
    final Bundle args = new Bundle();
    args.putInt(FIRST_PAGE_EXTRA, firstPage);
    args.putInt(MODE_EXTRA, mode);
    f.setArguments(args);
    return f;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    pageNumber = getArguments() != null ?
        getArguments().getInt(FIRST_PAGE_EXTRA) : -1;
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final Context context = getActivity();
    mainView = new TabletView(context);

    mode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
    if (mode == Mode.ARABIC) {
      mainView.init(TabletView.QURAN_PAGE, TabletView.QURAN_PAGE);
      leftImageView = ((QuranImagePageLayout) mainView.getLeftPage()).getImageView();
      rightImageView = ((QuranImagePageLayout) mainView.getRightPage()).getImageView();
      mainView.setPageController(this, pageNumber, pageNumber - 1);
    } else if (mode == Mode.TRANSLATION) {
      mainView.init(TabletView.TRANSLATION_PAGE, TabletView.TRANSLATION_PAGE);
      leftTranslation =
          ((QuranTranslationPageLayout) mainView.getLeftPage()).getTranslationView();
      rightTranslation =
          ((QuranTranslationPageLayout) mainView.getRightPage()).getTranslationView();

      leftTranslation.setTranslationClickedListener(
          () -> ((PagerActivity) getActivity()).toggleActionBar());
      rightTranslation.setTranslationClickedListener(
          () -> ((PagerActivity) getActivity()).toggleActionBar());
      mainView.setPageController(null, pageNumber, pageNumber - 1);
    }

    updateView();
    justCreated = true;

    lastHighlightedPage = 0;
    quranSettings = QuranSettings.getInstance(context);
    mOverlayText = quranSettings.shouldOverlayPageInfo();
    return mainView;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!justCreated) {
      updateView();
      if (mode == Mode.TRANSLATION) {
        rightTranslation.refresh();
        leftTranslation.refresh();
      }
    }
    justCreated = false;
  }

  public void updateView() {
    final Context context = getActivity();
    if (context == null || !isAdded()) {
      return;
    }

    final QuranSettings settings = QuranSettings.getInstance(context);
    final boolean useNewBackground = settings.useNewBackground();
    final boolean isNightMode = settings.isNightMode();
    mainView.updateView(isNightMode, useNewBackground);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) (context.getApplicationContext())).getApplicationComponent().inject(this);
    if (context instanceof AyahSelectedListener) {
      ayahSelectedListener = (AyahSelectedListener) context;
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    ayahSelectedListener = null;
    compositeDisposable.clear();
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    if (drawable != null && response != null) {
      final int page = response.getPageNumber();
      if (page == pageNumber - 1 && rightImageView != null) {
        rightPageLoadTask = null;
        rightImageView.setImageDrawable(drawable);
      } else if (page == pageNumber && leftImageView != null) {
        leftPageLoadTask = null;
        leftImageView.setImageDrawable(drawable);
      }
    }
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Context context = getActivity();
    if (mode == Mode.ARABIC) {
      if (PagerActivity.class.isInstance(getActivity())) {
        QuranPageWorker worker = ((PagerActivity) getActivity()).getQuranPageWorker();
        String widthParam = QuranScreenInfo.getInstance().getTabletWidthParam();
        rightPageLoadTask = worker.loadPage(widthParam, pageNumber - 1, this);
        leftPageLoadTask = worker.loadPage(widthParam, pageNumber, this);
      }

      new QueryPageCoordinatesTask(context).execute(pageNumber - 1, pageNumber);

      if (QuranSettings.getInstance(context).shouldHighlightBookmarks()) {
        highlightTagsTask();
      }
    } else if (mode == Mode.TRANSLATION) {
      if (context != null) {
        String database = quranSettings.getActiveTranslation();
        if (database != null) {
          new TranslationTask(context, pageNumber - 1, 0,
              database, rightTranslation).execute();
          new TranslationTask(context, pageNumber, 0,
              database, leftTranslation).execute();
        }
      }
    }
  }

  public void refresh(String database) {
    if (database != null) {
      Activity activity = getActivity();
      if (activity != null) {
        new TranslationTask(activity, pageNumber - 1, 0,
            database, rightTranslation).execute();
        new TranslationTask(activity, pageNumber, 0,
            database, leftTranslation).execute();
      }
    }
  }

  public void cleanup() {
    Timber.d("cleaning up page %d", pageNumber);
    if (leftPageLoadTask != null) {
      leftPageLoadTask.cancel(false);
    }

    if (rightPageLoadTask != null) {
      rightPageLoadTask.cancel(false);
    }

    if (leftImageView != null) {
      leftImageView.setImageDrawable(null);
      leftImageView = null;
    }

    if (rightImageView != null) {
      rightImageView.setImageDrawable(null);
      rightImageView = null;
    }
  }

  private void highlightTagsTask() {
    compositeDisposable.add(
        bookmarkModel.getBookmarkedAyahsOnPageObservable(pageNumber - 1, pageNumber)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableObserver<List<Bookmark>>() {
              @Override
              public void onNext(List<Bookmark> bookmarks) {
                if (mode == Mode.ARABIC && rightImageView != null && leftImageView != null) {
                  for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
                    Bookmark taggedAyah = bookmarks.get(i);
                    if (taggedAyah.page == pageNumber - 1) {
                      rightImageView.highlightAyah(
                          taggedAyah.sura, taggedAyah.ayah, HighlightType.BOOKMARK);
                    } else if (taggedAyah.page == pageNumber) {
                      leftImageView.highlightAyah(
                          taggedAyah.sura, taggedAyah.ayah, HighlightType.BOOKMARK);
                    }
                  }
                }
              }

              @Override
              public void onError(Throwable e) {
              }

              @Override
              public void onComplete() {
                if (coordinateData == null) {
                  new GetAyahCoordsTask(getActivity()).execute(pageNumber - 1, pageNumber);
                } else {
                  rightImageView.invalidate();
                  leftImageView.invalidate();
                }
              }
            }));
  }

  private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
    QueryPageCoordinatesTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getTabletWidthParam());
    }

    @Override
    protected void onPostExecute(RectF[] rect) {
      if (rect != null) {
        if (mode == Mode.ARABIC && rect.length == 2) {
          if (rightImageView != null && leftImageView != null) {
            rightImageView.setPageBounds(rect[0]);
            leftImageView.setPageBounds(rect[1]);
            if (mOverlayText && isAdded()) {
              Context context = getContext();
              String suraText = QuranInfo.getSuraNameFromPage(context, pageNumber - 1, true);
              String juzText = QuranInfo.getJuzString(context, pageNumber - 1);
              String pageText = QuranUtils.getLocalizedNumber(context, pageNumber - 1);
              String rub3Text =QuranDisplayHelper.displayRub3(context,pageNumber - 1);
              rightImageView.setOverlayText(suraText, juzText, pageText, rub3Text);
              suraText = QuranInfo.getSuraNameFromPage(context, pageNumber, true);
              juzText = QuranInfo.getJuzString(context, pageNumber);
              pageText = QuranUtils.getLocalizedNumber(context, pageNumber);
              rub3Text = QuranDisplayHelper.displayRub3(context,pageNumber);
              leftImageView.setOverlayText(suraText, juzText, pageText, rub3Text);
            }
          }
        }
      }
    }
  }

  private class GetAyahCoordsTask extends QueryAyahCoordsTask {

    GetAyahCoordsTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getTabletWidthParam());
    }

    GetAyahCoordsTask(Context context, MotionEvent event,
                      EventType eventType, int page) {
      super(context, event, eventType,
          QuranScreenInfo.getInstance().getTabletWidthParam(), page);
    }

    GetAyahCoordsTask(Context context, int sura,
                      int ayah, HighlightType type) {
      super(context,
          QuranScreenInfo.getInstance().getTabletWidthParam(),
          sura, ayah, type);
    }

    @Override
    protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps) {
      if (maps != null && maps.size() > 0) {
        if (mode == Mode.ARABIC && maps.size() == 2 &&
            rightImageView != null && leftImageView != null) {
          rightImageView.setCoordinateData(maps.get(0));
          leftImageView.setCoordinateData(maps.get(1));

          coordinateData = maps;

          if (mHighlightAyah) {
            handleHighlightAyah(mSura, mAyah, mHighlightType);
          } else if (mEvent != null) {
            handlePress(mEvent, mEventType, mPage);
          } else {
            rightImageView.invalidate();
            leftImageView.invalidate();
          }
        }
      }
    }
  }

  private boolean checkCoordinateData(MotionEvent event,
      EventType eventType, int page) {
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
    if (coordinateData == null) {
      new GetAyahCoordsTask(getActivity(), event, eventType, page)
          .execute(pageNumber - 1, pageNumber);
      return false;
    }
    // All good
    return true;
  }

  @Override
  public void highlightAyat(int page, Set<String> ayahKeys, HighlightType type) {
    final HighlightingImageView imageView;
    if (page == pageNumber - 1) {
      imageView = rightImageView;
    } else if (page == pageNumber) {
      imageView = leftImageView;
    } else {
      imageView = null;
    }

    if (imageView != null) {
      imageView.highlightAyat(ayahKeys, type);
      imageView.invalidate();
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    highlightAyah(sura, ayah, type, true);
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type, boolean scrollToAyah) {
    if (mode == Mode.ARABIC && coordinateData == null) {
      new GetAyahCoordsTask(getActivity(), sura, ayah, type)
          .execute(pageNumber - 1, pageNumber);
    } else {
      handleHighlightAyah(sura, ayah, type);
    }
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight) {
    final String key = sura + ":" + ayah;
    List<AyahBounds> bounds = null;
    if (coordinateData != null) {
      for (final Map<String, List<AyahBounds>> pageBounds : coordinateData) {
        if (pageBounds.containsKey(key)) {
          bounds = pageBounds.get(key);
          break;
        }
      }
    }

    final int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
    final ImageView imageView;
    if (page == pageNumber - 1) {
      imageView = rightImageView;
    } else if (page == pageNumber) {
      imageView = leftImageView;
    } else {
      imageView = null;
    }

    final int width = imageView != null ? imageView.getWidth() : 0;
    if (bounds != null && width > 0) {
      final int screenHeight = QuranScreenInfo.getInstance().getHeight();
      final AyahToolBar.AyahToolBarPosition result =
          ImageAyahUtils.getToolBarPosition(bounds, imageView.getImageMatrix(),
              width, screenHeight, toolBarWidth, toolBarHeight);
      if (page == pageNumber - 1) {
        // right page, need to adjust offset
        result.x += width;
      }
      return result;
    }
    return null;
  }

  private void handleHighlightAyah(int sura, int ayah, HighlightType type) {
    int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
    if (mode == Mode.ARABIC) {
      if (leftImageView == null || rightImageView == null) {
        return;
      }

      if (page == pageNumber - 1) {
        rightImageView.highlightAyah(sura, ayah, type);
        rightImageView.invalidate();
        if (lastHighlightedPage == pageNumber) {
          leftImageView.unHighlight(type);
        }
      } else if (page == pageNumber) {
        leftImageView.highlightAyah(sura, ayah, type);
        leftImageView.invalidate();
        if (lastHighlightedPage == pageNumber - 1) {
          rightImageView.unHighlight(type);
        }
      }
      lastHighlightedPage = page;
    } else if (mode == Mode.TRANSLATION) {
      int ayahId = QuranInfo.getAyahId(sura, ayah);
      if (leftTranslation == null || rightTranslation == null) {
        return;
      }
      if (page == pageNumber - 1) {
        rightTranslation.highlightAyah(ayahId);
        leftTranslation.unhighlightAyat();
      } else {
        leftTranslation.highlightAyah(ayahId);
        rightTranslation.unhighlightAyat();
      }
    }
  }

  @Override
  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    if (mode == Mode.ARABIC) {
      int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
      if (page == pageNumber - 1 && rightImageView != null) {
        rightImageView.unHighlight(sura, ayah, type);
      } else if (page == pageNumber && leftImageView != null) {
        leftImageView.unHighlight(sura, ayah, type);
      }
    }
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    if (mode == Mode.ARABIC) {
      if (rightImageView != null) {
        rightImageView.unHighlight(type);
      }
      if (leftImageView != null) {
        leftImageView.unHighlight(type);
      }
    }
  }

  private void handlePress(MotionEvent event, EventType eventType, int page) {
    QuranAyah result = getAyahFromCoordinates(
        page, event.getX(), event.getY());
    if (result != null && ayahSelectedListener != null) {
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      ayahSelectedListener.onAyahSelected(eventType, suraAyah, this);
    }
  }

  private QuranAyah getAyahFromCoordinates(int page, float xc, float yc) {
    if (coordinateData == null) {
      return null;
    }

    Map<String, List<AyahBounds>> coords;
    if (coordinateData.size() == 1) {
      coords = coordinateData.get(0);
    } else {
      if (page == pageNumber - 1) {
        coords = coordinateData.get(0);
      } else {
        coords = coordinateData.get(1);
      }
    }

    HighlightingImageView imageView;
    if (page == pageNumber - 1) {
      imageView = rightImageView;
    } else {
      imageView = leftImageView;
    }

    return ImageAyahUtils.getAyahFromCoordinates(coords, imageView, xc, yc);
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event,
      EventType eventType, int page) {
    if (eventType == EventType.DOUBLE_TAP) {
      unHighlightAyahs(HighlightType.SELECTION);
    }
    if (ayahSelectedListener == null) return false;
    if (ayahSelectedListener.isListeningForAyahSelection(eventType)) {
      if (checkCoordinateData(event, eventType, page)) {
        handlePress(event, eventType, page);
      }
      return true;
    } else {
      return ayahSelectedListener.onClick(eventType);
    }
  }

  @Override
  public void handleRetryClicked() {
    // currently no-op - we don't show retry button in tablet right now,
    // though we should since it's now easy.
  }

  @Override
  public void onScrollChanged(int x, int y, int oldx, int oldy) {
    // no-op - no image ScrollView in this mode.
  }
}
