package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.task.QueryAyahCoordsTask;
import com.quran.labs.androidquran.task.QueryPageCoordsTask;
import com.quran.labs.androidquran.task.TranslationTask;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
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
import com.quran.labs.androidquran.widgets.QuranTranslationPageLayout;
import com.quran.labs.androidquran.widgets.TabletView;
import com.quran.labs.androidquran.widgets.TranslationView;

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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.subscriptions.CompositeSubscription;
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

  private int mMode;
  private int mPageNumber;
  private boolean mOverlayText;
  private int mLastHighlightedPage;
  private AyahSelectedListener mAyahSelectedListener;
  private List<Map<String, List<AyahBounds>>> mCoordinateData;
  private TranslationView mLeftTranslation, mRightTranslation = null;
  private HighlightingImageView mLeftImageView, mRightImageView = null;
  private QuranSettings mQuranSettings;
  private BookmarkModel mBookmarkModel;
  private CompositeSubscription mCompositeSubscription;

  private TabletView mMainView;

  private Future<?> mLeftPageLoadTask;
  private Future<?> mRightPageLoadTask;

  private boolean mJustCreated;

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
    mPageNumber = getArguments() != null ?
        getArguments().getInt(FIRST_PAGE_EXTRA) : -1;
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final Context context = getActivity();
    mMainView = new TabletView(context);

    mMode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
    if (mMode == Mode.ARABIC) {
      mMainView.init(TabletView.QURAN_PAGE, TabletView.QURAN_PAGE);
      mLeftImageView =
          ((QuranImagePageLayout) mMainView.getLeftPage()).getImageView();
      mRightImageView =
          ((QuranImagePageLayout) mMainView.getRightPage()).getImageView();
      mMainView.setPageController(this, mPageNumber, mPageNumber - 1);
    } else if (mMode == Mode.TRANSLATION) {
      mMainView.init(TabletView.TRANSLATION_PAGE, TabletView.TRANSLATION_PAGE);
      mLeftTranslation =
          ((QuranTranslationPageLayout) mMainView.getLeftPage())
              .getTranslationView();
      mRightTranslation =
          ((QuranTranslationPageLayout) mMainView.getRightPage())
              .getTranslationView();

      mLeftTranslation.setTranslationClickedListener(
          new TranslationView.TranslationClickedListener() {
            @Override
            public void onTranslationClicked() {
              ((PagerActivity) getActivity()).toggleActionBar();
            }
          });
      mRightTranslation.setTranslationClickedListener(
          new TranslationView.TranslationClickedListener() {
            @Override
            public void onTranslationClicked() {
              ((PagerActivity) getActivity()).toggleActionBar();
            }
          });
      mMainView.setPageController(null, mPageNumber, mPageNumber - 1);
    }
    mBookmarkModel = BookmarkModel.getInstance(context);

    updateView();
    mJustCreated = true;

    mLastHighlightedPage = 0;
    mQuranSettings = QuranSettings.getInstance(context);
    mOverlayText = mQuranSettings.shouldOverlayPageInfo();
    return mMainView;
  }

  @Override
  public void onResume() {
    super.onResume();
    if (!mJustCreated) {
      updateView();
      if (mMode == Mode.TRANSLATION) {
        mRightTranslation.refresh();
        mLeftTranslation.refresh();
      }
    }
    mJustCreated = false;
  }

  public void updateView() {
    final Context context = getActivity();
    if (context == null || !isAdded()) {
      return;
    }

    final QuranSettings settings = QuranSettings.getInstance(context);
    final boolean useNewBackground = settings.useNewBackground();
    final boolean isNightMode = settings.isNightMode();
    mMainView.updateView(isNightMode, useNewBackground);
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    if (context instanceof AyahSelectedListener) {
      mAyahSelectedListener = (AyahSelectedListener) context;
    }
    mCompositeSubscription = new CompositeSubscription();
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mAyahSelectedListener = null;
    mCompositeSubscription.unsubscribe();
  }

  @Override
  public void onLoadImageResponse(BitmapDrawable drawable, Response response) {
    if (drawable != null && response != null) {
      final int page = response.getPageNumber();
      if (page == mPageNumber - 1 && mRightImageView != null) {
        mRightPageLoadTask = null;
        mRightImageView.setImageDrawable(drawable);
      } else if (page == mPageNumber && mLeftImageView != null) {
        mLeftPageLoadTask = null;
        mLeftImageView.setImageDrawable(drawable);
      }
    }
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    Context context = getActivity();
    if (mMode == Mode.ARABIC) {
      if (PagerActivity.class.isInstance(getActivity())) {
        QuranPageWorker worker =
            ((PagerActivity) getActivity()).getQuranPageWorker();
        String widthParam =
            QuranScreenInfo.getInstance().getTabletWidthParam();
        mRightPageLoadTask =
            worker.loadPage(widthParam, mPageNumber - 1, this);
        mLeftPageLoadTask =
            worker.loadPage(widthParam, mPageNumber, this);
      }

      new QueryPageCoordinatesTask(context)
          .execute(mPageNumber - 1, mPageNumber);

      if (QuranSettings.getInstance(context).shouldHighlightBookmarks()) {
        highlightTagsTask();
      }
    } else if (mMode == Mode.TRANSLATION) {
      if (context != null) {
        String database = mQuranSettings.getActiveTranslation();
        if (database != null) {
          new TranslationTask(context, mPageNumber - 1, 0,
              database, mRightTranslation).execute();
          new TranslationTask(context, mPageNumber, 0,
              database, mLeftTranslation).execute();
        }
      }
    }
  }

  public void refresh(String database) {
    if (database != null) {
      Activity activity = getActivity();
      if (activity != null) {
        new TranslationTask(activity, mPageNumber - 1, 0,
            database, mRightTranslation).execute();
        new TranslationTask(activity, mPageNumber, 0,
            database, mLeftTranslation).execute();
      }
    }
  }

  public void cleanup() {
    Timber.d("cleaning up page " + mPageNumber);
    if (mLeftPageLoadTask != null) {
      mLeftPageLoadTask.cancel(false);
    }

    if (mRightPageLoadTask != null) {
      mRightPageLoadTask.cancel(false);
    }

    if (mLeftImageView != null) {
      mLeftImageView.setImageDrawable(null);
      mLeftImageView = null;
    }

    if (mRightImageView != null) {
      mRightImageView.setImageDrawable(null);
      mRightImageView = null;
    }
  }

  private void highlightTagsTask() {
    Subscription s =
        mBookmarkModel.getBookmarkedAyahsOnPageObservable(mPageNumber - 1, mPageNumber)
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Subscriber<List<Bookmark>>() {
          @Override
          public void onCompleted() {
            if (mCoordinateData == null) {
              new GetAyahCoordsTask(getActivity()).execute(mPageNumber - 1, mPageNumber);
            } else {
              mRightImageView.invalidate();
              mLeftImageView.invalidate();
            }
          }

          @Override
          public void onError(Throwable e) {
          }

          @Override
          public void onNext(List<Bookmark> bookmarks) {
            if (mMode == Mode.ARABIC && mRightImageView != null && mLeftImageView != null) {
              for (int i = 0, bookmarksSize = bookmarks.size(); i < bookmarksSize; i++) {
                Bookmark taggedAyah = bookmarks.get(i);
                if (taggedAyah.page == mPageNumber - 1) {
                  mRightImageView.highlightAyah(
                      taggedAyah.sura, taggedAyah.ayah, HighlightType.BOOKMARK);
                } else if (taggedAyah.page == mPageNumber) {
                  mLeftImageView.highlightAyah(
                      taggedAyah.sura, taggedAyah.ayah, HighlightType.BOOKMARK);
                }
              }
            }
          }
        });
    mCompositeSubscription.add(s);
  }

  private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
    public QueryPageCoordinatesTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getTabletWidthParam());
    }

    @Override
    protected void onPostExecute(RectF[] rect) {
      if (rect != null) {
        if (mMode == Mode.ARABIC && rect.length == 2) {
          if (mRightImageView != null && mLeftImageView != null) {
            mRightImageView.setPageBounds(rect[0]);
            mLeftImageView.setPageBounds(rect[1]);
            if (mOverlayText) {
              mRightImageView.setOverlayText(mPageNumber - 1, true);
              mLeftImageView.setOverlayText(mPageNumber, true);
            }
          }
        }
      }
    }
  }

  private class GetAyahCoordsTask extends QueryAyahCoordsTask {

    public GetAyahCoordsTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getTabletWidthParam());
    }

    public GetAyahCoordsTask(Context context, MotionEvent event,
        EventType eventType, int page) {
      super(context, event, eventType,
          QuranScreenInfo.getInstance().getTabletWidthParam(), page);
    }

    public GetAyahCoordsTask(Context context, int sura,
        int ayah, HighlightType type) {
      super(context,
          QuranScreenInfo.getInstance().getTabletWidthParam(),
          sura, ayah, type);
    }

    @Override
    protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps) {
      if (maps != null && maps.size() > 0) {
        if (mMode == Mode.ARABIC && maps.size() == 2 &&
            mRightImageView != null && mLeftImageView != null) {
          mRightImageView.setCoordinateData(maps.get(0));
          mLeftImageView.setCoordinateData(maps.get(1));

          mCoordinateData = maps;

          if (mHighlightAyah) {
            handleHighlightAyah(mSura, mAyah, mHighlightType);
          } else if (mEvent != null) {
            handlePress(mEvent, mEventType, mPage);
          } else {
            mRightImageView.invalidate();
            mLeftImageView.invalidate();
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
    if (mCoordinateData == null) {
      new GetAyahCoordsTask(getActivity(), event, eventType, page)
          .execute(mPageNumber - 1, mPageNumber);
      return false;
    }
    // All good
    return true;
  }

  @Override
  public void highlightAyat(
      int page, Set<String> ayahKeys, HighlightType type) {
    final HighlightingImageView imageView;
    if (page == mPageNumber - 1) {
      imageView = mRightImageView;
    } else if (page == mPageNumber) {
      imageView = mLeftImageView;
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
    if (mMode == Mode.ARABIC && mCoordinateData == null) {
      new GetAyahCoordsTask(getActivity(), sura, ayah, type)
          .execute(mPageNumber - 1, mPageNumber);
    } else {
      handleHighlightAyah(sura, ayah, type);
    }
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
      int toolBarWidth, int toolBarHeight) {
    final String key = sura + ":" + ayah;
    List<AyahBounds> bounds = null;
    if (mCoordinateData != null) {
      for (final Map<String, List<AyahBounds>> pageBounds : mCoordinateData) {
        if (pageBounds.containsKey(key)) {
          bounds = pageBounds.get(key);
          break;
        }
      }
    }

    final int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
    final ImageView imageView;
    if (page == mPageNumber - 1) {
      imageView = mRightImageView;
    } else if (page == mPageNumber) {
      imageView = mLeftImageView;
    } else {
      imageView = null;
    }

    final int width = imageView != null ? imageView.getWidth() : 0;
    if (bounds != null && width > 0) {
      final int screenHeight = QuranScreenInfo.getInstance().getHeight();
      final AyahToolBar.AyahToolBarPosition result =
          ImageAyahUtils.getToolBarPosition(bounds, imageView.getImageMatrix(),
              width, screenHeight, toolBarWidth, toolBarHeight);
      if (page == mPageNumber - 1) {
        // right page, need to adjust offset
        result.x += width;
      }
      return result;
    }
    return null;
  }

  private void handleHighlightAyah(int sura, int ayah, HighlightType type) {
    int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
    if (mMode == Mode.ARABIC) {
      if (mLeftImageView == null || mRightImageView == null) {
        return;
      }

      if (page == mPageNumber - 1) {
        mRightImageView.highlightAyah(sura, ayah, type);
        mRightImageView.invalidate();
        if (mLastHighlightedPage == mPageNumber) {
          mLeftImageView.unHighlight(type);
        }
      } else if (page == mPageNumber) {
        mLeftImageView.highlightAyah(sura, ayah, type);
        mLeftImageView.invalidate();
        if (mLastHighlightedPage == mPageNumber - 1) {
          mRightImageView.unHighlight(type);
        }
      }
      mLastHighlightedPage = page;
    } else if (mMode == Mode.TRANSLATION) {
      int ayahId = QuranInfo.getAyahId(sura, ayah);
      if (mLeftTranslation == null || mRightTranslation == null) {
        return;
      }
      if (page == mPageNumber - 1) {
        mRightTranslation.highlightAyah(ayahId);
        mLeftTranslation.unhighlightAyat();
      } else {
        mLeftTranslation.highlightAyah(ayahId);
        mRightTranslation.unhighlightAyat();
      }
    }
  }

  @Override
  public void unHighlightAyah(int sura, int ayah, HighlightType type) {
    if (mMode == Mode.ARABIC) {
      int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
      if (page == mPageNumber - 1 && mRightImageView != null) {
        mRightImageView.unHighlight(sura, ayah, type);
      } else if (page == mPageNumber && mLeftImageView != null) {
        mLeftImageView.unHighlight(sura, ayah, type);
      }
    }
  }

  @Override
  public void unHighlightAyahs(HighlightType type) {
    if (mMode == Mode.ARABIC) {
      if (mRightImageView != null) {
        mRightImageView.unHighlight(type);
      }
      if (mLeftImageView != null) {
        mLeftImageView.unHighlight(type);
      }
    }
  }

  private void handlePress(MotionEvent event, EventType eventType, int page) {
    QuranAyah result = getAyahFromCoordinates(
        page, event.getX(), event.getY());
    if (result != null && mAyahSelectedListener != null) {
      /*HighlightingImageView hv = null;
      if (mMode == Mode.ARABIC) {
        // TODO use getHighlightingImageView?
        if (page == mPageNumber - 1) {
          hv = mRightImageView;
          // TODO unhighlight the other page?
        } else if (page == mPageNumber) {
          hv = mLeftImageView;
          // TODO unhighlight the other page?
        }
      }*/
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      mAyahSelectedListener.onAyahSelected(eventType, suraAyah, this);
    }
  }

  private QuranAyah getAyahFromCoordinates(int page, float xc, float yc) {
    if (mCoordinateData == null) {
      return null;
    }

    Map<String, List<AyahBounds>> coords;
    if (mCoordinateData.size() == 1) {
      coords = mCoordinateData.get(0);
    } else {
      if (page == mPageNumber - 1) {
        coords = mCoordinateData.get(0);
      } else {
        coords = mCoordinateData.get(1);
      }
    }

    HighlightingImageView imageView;
    if (page == mPageNumber - 1) {
      imageView = mRightImageView;
    } else {
      imageView = mLeftImageView;
    }

    return ImageAyahUtils.getAyahFromCoordinates(coords, imageView, xc, yc);
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event,
      EventType eventType, int page) {
    if (eventType == EventType.DOUBLE_TAP) {
      unHighlightAyahs(HighlightType.SELECTION);
    }
    if (mAyahSelectedListener == null) return false;
    if (mAyahSelectedListener.isListeningForAyahSelection(eventType)) {
      if (checkCoordinateData(event, eventType, page)) {
        handlePress(event, eventType, page);
      }
      return true;
    } else {
      return mAyahSelectedListener.onClick(eventType);
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
