package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.HighlightInfo;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.common.Response;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.model.bookmark.BookmarkModel;
import com.quran.labs.androidquran.model.quran.CoordinatesModel;
import com.quran.labs.androidquran.presenter.quran.QuranPagePresenter;
import com.quran.labs.androidquran.presenter.quran.QuranPageScreen;
import com.quran.labs.androidquran.presenter.translation.TranslationPresenter;
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
import com.quran.labs.androidquran.widgets.QuranTranslationPageLayout;
import com.quran.labs.androidquran.widgets.TabletView;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import timber.log.Timber;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class TabletFragment extends Fragment
    implements AyahTracker, PageController, PageDownloadListener,
    TranslationPresenter.TranslationScreen, QuranPageScreen {
  private static final String FIRST_PAGE_EXTRA = "pageNumber";
  private static final String MODE_EXTRA = "mode";

  public static class Mode {
    public static final int ARABIC = 1;
    public static final int TRANSLATION = 2;
    public static final int MIXED = 3;
  }

  private int mode;
  private int pageNumber;
  private boolean overlayText;
  private int lastHighlightedPage;
  private boolean ayahCoordinatesError;
  private AyahSelectedListener ayahSelectedListener;
  private List<Map<String, List<AyahBounds>>> coordinateData;
  private TranslationView leftTranslation, rightTranslation = null;
  private HighlightingImageView leftImageView, rightImageView = null;
  private CompositeDisposable compositeDisposable = new CompositeDisposable();

  private TranslationPresenter leftPageTranslationPresenter;
  private TranslationPresenter rightPageTranslationPresenter;
  private QuranPagePresenter quranPagePresenter;

  private TabletView mainView;

  @Inject BookmarkModel bookmarkModel;
  @Inject QuranPageWorker quranPageWorker;
  @Inject CoordinatesModel coordinatesModel;

  private Future<?> leftPageLoadTask;
  private Future<?> rightPageLoadTask;
  private HighlightInfo highlightInfo;

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

    mode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
    if (mode == Mode.TRANSLATION) {
      Context context = getContext();
      leftPageTranslationPresenter = new TranslationPresenter(context, pageNumber);
      rightPageTranslationPresenter = new TranslationPresenter(context, pageNumber - 1);

      leftPageTranslationPresenter.bind(this);
      rightPageTranslationPresenter.bind(this);
    }
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

    lastHighlightedPage = 0;
    overlayText = QuranSettings.getInstance(context).shouldOverlayPageInfo();
    return mainView;
  }

  @Override
  public void onDestroy() {
    if (rightPageTranslationPresenter != null) {
      leftPageTranslationPresenter.unbind(this);
      rightPageTranslationPresenter.unbind(this);
    }
    super.onDestroy();
  }

  @Override
  public void onStart() {
    super.onStart();
    quranPagePresenter.bind(this);
  }

  @Override
  public void onStop() {
    quranPagePresenter.unbind(this);
    super.onStop();
  }

  @Override
  public void onResume() {
    super.onResume();
    updateView();
    if (mode == Mode.TRANSLATION) {
      rightTranslation.refresh();
      leftTranslation.refresh();
    }
  }

  public void updateView() {
    if (isAdded()) {
      final QuranSettings settings = QuranSettings.getInstance(getContext());
      final boolean useNewBackground = settings.useNewBackground();
      final boolean isNightMode = settings.isNightMode();
      mainView.updateView(isNightMode, useNewBackground);
    }
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) (context.getApplicationContext())).getApplicationComponent().inject(this);
    if (context instanceof AyahSelectedListener) {
      ayahSelectedListener = (AyahSelectedListener) context;
    }

    coordinateData = new ArrayList<>(2);
    coordinateData.add(new HashMap<>());
    coordinateData.add(new HashMap<>());

    int page = getArguments().getInt(FIRST_PAGE_EXTRA);
    quranPagePresenter = new QuranPagePresenter(bookmarkModel, coordinatesModel,
        QuranSettings.getInstance(context), true, page - 1, page);
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
    if (mode == Mode.ARABIC) {
      String widthParam = QuranScreenInfo.getInstance().getTabletWidthParam();
      rightPageLoadTask = quranPageWorker.loadPage(widthParam, pageNumber - 1, this);
      leftPageLoadTask = quranPageWorker.loadPage(widthParam, pageNumber, this);
    } else if (mode == Mode.TRANSLATION) {
      leftPageTranslationPresenter.refresh();
      rightPageTranslationPresenter.refresh();
    }
  }

  @Override
  public void setVerses(int page, @NonNull List<QuranAyah> verses) {
    if (page == pageNumber) {
      leftTranslation.setAyahs(verses);
    } else if (page == pageNumber - 1) {
      rightTranslation.setAyahs(verses);
    }
  }

  public void refresh() {
    leftPageTranslationPresenter.refresh();
    rightPageTranslationPresenter.refresh();
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

  @Override
  public void setBookmarksOnPage(List<Bookmark> bookmarks) {
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

      if (coordinateData.get(0).size() + coordinateData.get(1).size() > 0) {
        rightImageView.invalidate();
        leftImageView.invalidate();
      }
    }
  }

  @Override
  public void setPageCoordinates(int page, RectF pageCoordinates) {
    if (mode == Mode.ARABIC) {
      HighlightingImageView imageView = page == pageNumber ? leftImageView : rightImageView;
      if (imageView != null) {
        imageView.setPageBounds(pageCoordinates);
        if (overlayText && isAdded()) {
          Context context = getContext();
          String suraText = QuranInfo.getSuraNameFromPage(context, page, true);
          String juzText = QuranInfo.getJuzString(context, page);
          String pageText = QuranUtils.getLocalizedNumber(context, page);
          String rub3Text = QuranDisplayHelper.displayRub3(context, page);
          imageView.setOverlayText(suraText, juzText, pageText, rub3Text);
        }
      }
    }
  }

  @Override
  public void setAyahCoordinatesError() {
    ayahCoordinatesError = true;
  }

  @Override
  public void setAyahCoordinatesData(int page, Map<String, List<AyahBounds>> coordinates) {
    HighlightingImageView imageView = page == pageNumber ? leftImageView : rightImageView;
    if (mode == Mode.ARABIC && imageView != null) {
      imageView.setCoordinateData(coordinates);
      coordinateData.set(pageNumber - page, coordinates);

      if (highlightInfo != null) {
        handleHighlightAyah(highlightInfo.sura, highlightInfo.ayah, highlightInfo.highlightType);
        highlightInfo = null;
      } else {
        rightImageView.invalidate();
        leftImageView.invalidate();
      }
    }
  }

  private void checkCoordinateData() {
    Activity activity = getActivity();
    if (activity instanceof PagerActivity &&
        (!QuranFileUtils.haveAyaPositionFile(activity) ||
            !QuranFileUtils.hasArabicSearchDatabase(activity))) {
      PagerActivity pagerActivity = (PagerActivity) activity;
      pagerActivity.showGetRequiredFilesDialog();
    }
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
    boolean highlight = true;
    if (mode == Mode.ARABIC) {
      int index = pageNumber - QuranInfo.getPageFromSuraAyah(sura, ayah);
      if (coordinateData.get(index).isEmpty()) {
        highlightInfo = new HighlightInfo(sura, ayah, type, scrollToAyah);
        highlight = false;
      }
    }

    if (highlight) {
      handleHighlightAyah(sura, ayah, type);
    }
  }

  @Override
  public AyahToolBar.AyahToolBarPosition getToolBarPosition(int sura, int ayah,
                                                            int toolBarWidth, int toolBarHeight) {
    final String key = sura + ":" + ayah;
    List<AyahBounds> bounds = null;
    if (coordinateData.get(0).size() + coordinateData.get(1).size() > 0) {
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
      HighlightingImageView imageView = page == pageNumber ? leftImageView : rightImageView;
      HighlightingImageView other = imageView == leftImageView ? rightImageView : leftImageView;
      if (imageView != null) {
        imageView.highlightAyah(sura, ayah, type);
        imageView.invalidate();
        if (lastHighlightedPage != page && other != null) {
          other.unHighlight(type);
        }
      }
      lastHighlightedPage = page;
    } else if (mode == Mode.TRANSLATION) {
      int ayahId = QuranInfo.getAyahId(sura, ayah);
      TranslationView active = page == pageNumber ? leftTranslation : rightTranslation;
      TranslationView other = active == leftTranslation ? rightTranslation : leftTranslation;
      if (active != null) {
        active.highlightAyah(ayahId);
        if (other != null) {
          other.unhighlightAyat();
        }
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
    int index = pageNumber - page;
    if (coordinateData.size() > index) {
      Map<String, List<AyahBounds>> coords = coordinateData.get(index);
      if (!coords.isEmpty()) {
        HighlightingImageView imageView = index == 0 ? leftImageView : rightImageView;
        return ImageAyahUtils.getAyahFromCoordinates(coords, imageView, xc, yc);
      }
    }
    return null;
  }

  @Override
  public boolean handleTouchEvent(MotionEvent event,
                                  EventType eventType, int page) {
    if (eventType == EventType.DOUBLE_TAP) {
      unHighlightAyahs(HighlightType.SELECTION);
    }
    if (ayahSelectedListener == null) return false;
    if (ayahSelectedListener.isListeningForAyahSelection(eventType)) {
      if (ayahCoordinatesError) {
        checkCoordinateData();
      } else {
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
