package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.*;
import android.widget.ImageView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.*;
import com.quran.labs.androidquran.ui.util.*;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.labs.androidquran.util.QuranScreenInfo;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.List;
import java.util.Map;

import static com.quran.labs.androidquran.ui.helpers.AyahSelectedListener.EventType;

public class TabletFragment extends SherlockFragment implements AyahTracker {

  private static final String TAG = "TabletFragment";
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
  private PaintDrawable mLeftGradient, mRightGradient = null;
  private TranslationView mLeftTranslation, mRightTranslation = null;
  private HighlightingImageView mLeftImageView, mRightImageView = null;

  private View mMainView;
  private ImageView mLeftBorder, mRightBorder, mLine;
  private View mLeftArea, mRightArea;

  private boolean mJustCreated;
  private Resources mResources;
  private SharedPreferences mPrefs;

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
    int width = getActivity().getWindowManager()
        .getDefaultDisplay().getWidth() / 2;
    mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
    mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(LayoutInflater inflater,
                           ViewGroup container, Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.tablet_layout,
        container, false);

    mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    mResources = getResources();

    mLeftArea = view.findViewById(R.id.left_page_area);
    mRightArea = view.findViewById(R.id.right_page_area);

    mLeftBorder = (ImageView) view.findViewById(R.id.left_border);
    mRightBorder = (ImageView) view.findViewById(R.id.right_border);
    mLine = (ImageView) view.findViewById(R.id.line);

    mLeftImageView = (HighlightingImageView) view
        .findViewById(R.id.left_page_image);
    mRightImageView = (HighlightingImageView) view
        .findViewById(R.id.right_page_image);
    mLeftTranslation = (TranslationView) view
        .findViewById(R.id.left_page_translation);
    mRightTranslation = (TranslationView) view
        .findViewById(R.id.right_page_translation);

    mMode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
    if (mMode == Mode.ARABIC) {
      mLeftTranslation.setVisibility(View.GONE);
      mRightTranslation.setVisibility(View.GONE);

      mLeftImageView.setVisibility(View.VISIBLE);
      mRightImageView.setVisibility(View.VISIBLE);

      final GestureDetector rightGestureDetector = new GestureDetector(
          getActivity(), new PageGestureDetector(mPageNumber - 1));
      View.OnTouchListener gestureListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
          return rightGestureDetector.onTouchEvent(event);
        }
      };
      mRightImageView.setOnTouchListener(gestureListener);
      mRightImageView.setClickable(true);
      mRightImageView.setLongClickable(true);

      final GestureDetector leftGestureDetector = new GestureDetector(
          getActivity(), new PageGestureDetector(mPageNumber));
      View.OnTouchListener leftGestureListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
          return leftGestureDetector.onTouchEvent(event);
        }
      };
      mLeftImageView.setOnTouchListener(leftGestureListener);
      mLeftImageView.setClickable(true);
      mLeftImageView.setLongClickable(true);
    } else if (mMode == Mode.TRANSLATION) {
      mLeftImageView.setVisibility(View.GONE);
      mRightImageView.setVisibility(View.GONE);

      mLeftTranslation.setVisibility(View.VISIBLE);
      mRightTranslation.setVisibility(View.VISIBLE);

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
    }

    mMainView = view;
    updateView();
    mJustCreated = true;

    mLastHighlightedPage = 0;
    mOverlayText = mPrefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);
    return view;
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

  private void updateView() {
    int leftBorderImageId = R.drawable.border_left;
    int rightBorderImageId = R.drawable.border_right;
    int lineImageId = R.drawable.dark_line;

    Context context = getActivity();
    if (context == null || mResources == null ||
        mMainView == null || !isAdded()) {
      return;
    }

    if (!mPrefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true)) {
      int color = mResources.getColor(R.color.page_background);
      mLeftArea.setBackgroundColor(color);
      mRightArea.setBackgroundColor(color);
    } else {
      mLeftArea.setBackgroundDrawable(mLeftGradient);
      mRightArea.setBackgroundDrawable(mRightGradient);
    }

    boolean nightMode = false;
    int nightModeTextBrightness =
        Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
    if (mPrefs.getBoolean(Constants.PREF_NIGHT_MODE, false)) {
      leftBorderImageId = R.drawable.night_left_border;
      rightBorderImageId = R.drawable.night_right_border;
      lineImageId = R.drawable.light_line;
      mLeftArea.setBackgroundColor(Color.BLACK);
      mRightArea.setBackgroundColor(Color.BLACK);
      nightMode = true;
      nightModeTextBrightness =
          QuranSettings.getNightModeTextBrightness(context);
    }

    mLeftBorder.setBackgroundResource(leftBorderImageId);
    mRightBorder.setBackgroundResource(rightBorderImageId);
    mLine.setImageResource(lineImageId);


    if (mMode == Mode.ARABIC) {
      mLeftImageView.setNightMode(nightMode);
      mRightImageView.setNightMode(nightMode);
      if (nightMode) {
        mLeftImageView.setNightModeTextBrightness(nightModeTextBrightness);
        mRightImageView.setNightModeTextBrightness(nightModeTextBrightness);
      }
    }

    if (!mPrefs.getBoolean(Constants.PREF_HIGHLIGHT_BOOKMARKS, true)) {
      mRightImageView.unHighlight(HighlightType.BOOKMARK);
      mLeftImageView.unHighlight(HighlightType.BOOKMARK);
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

    Context context = getActivity();
    if (mMode == Mode.ARABIC) {
      if (PagerActivity.class.isInstance(getActivity())) {
        QuranPageWorker worker =
            ((PagerActivity) getActivity()).getQuranPageWorker();
        String widthParam =
            QuranScreenInfo.getInstance().getTabletWidthParam();
        worker.loadPage(widthParam, mPageNumber - 1, mRightImageView);
        worker.loadPage(widthParam, mPageNumber, mLeftImageView);
      }

      new QueryPageCoordinatesTask(context)
          .execute(mPageNumber - 1, mPageNumber);

      if (QuranSettings.shouldHighlightBookmarks(context)) {
        new HighlightTagsTask(context)
            .execute(mPageNumber - 1, mPageNumber);
      }
    } else if (mMode == Mode.TRANSLATION) {
      if (context != null) {
        SharedPreferences prefs =
            PreferenceManager.getDefaultSharedPreferences(context);
        String database = prefs.getString(
            Constants.PREF_ACTIVE_TRANSLATION, null);
        if (database != null) {
          new TranslationTask(context, mPageNumber - 1, 0,
              database, mRightTranslation).execute();
          new TranslationTask(context, mPageNumber, 0,
              database, mLeftTranslation).execute();
        }
      }
    }
  }

  public void cleanup() {
    android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
    if (mLeftImageView != null) {
      mLeftImageView.setImageDrawable(null);
      mLeftImageView = null;
    }

    if (mRightImageView != null) {
      mRightImageView.setImageDrawable(null);
      mRightImageView = null;
    }
  }

  private class HighlightTagsTask extends QueryBookmarkedAyahsTask {

    public HighlightTagsTask(Context context) {
      super(context);
    }

    @Override
    protected void onPostExecute(List<BookmarksDBAdapter.Bookmark> result) {
      if (result != null && !result.isEmpty() && mMode == Mode.ARABIC &&
          mRightImageView != null && mLeftImageView != null) {
        for (BookmarksDBAdapter.Bookmark taggedAyah : result) {
          if (taggedAyah.mPage == mPageNumber - 1) {
            mRightImageView.highlightAyah(
                taggedAyah.mSura, taggedAyah.mAyah, HighlightType.BOOKMARK);
          } else if (taggedAyah.mPage == mPageNumber) {
            mLeftImageView.highlightAyah(
                taggedAyah.mSura, taggedAyah.mAyah, HighlightType.BOOKMARK);
          }
        }
        if (mCoordinateData == null) {
          new GetAyahCoordsTask(getActivity()).execute(mPageNumber - 1, mPageNumber);
        } else {
          mRightImageView.invalidate();
          mLeftImageView.invalidate();
        }
      }
    }
  }

  private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
    public QueryPageCoordinatesTask(Context context) {
      super(context, QuranScreenInfo.getInstance().getTabletWidthParam());
    }

    @Override
    protected void onPostExecute(Rect[] rect) {
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

    public GetAyahCoordsTask(Context context, MotionEvent event, EventType eventType, int page) {
      super(context, event, eventType,
          QuranScreenInfo.getInstance().getTabletWidthParam(), page);
    }

    public GetAyahCoordsTask(Context context, int sura, int ayah, HighlightType type) {
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

  private class PageGestureDetector extends GestureDetector.SimpleOnGestureListener {
    private int mDetectedPage;

    public PageGestureDetector(int page) {
      mDetectedPage = page;
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
        new GetAyahCoordsTask(getActivity(), event, eventType,
            mDetectedPage).execute(mPageNumber - 1, mPageNumber);
        return false;
      }
      // All good
      return true;
    }

    private boolean handleEvent(MotionEvent event, EventType eventType) {
      if (mAyahSelectedListener == null) return false;
      if (mAyahSelectedListener.isListeningForAyahSelection(eventType)) {
        if (checkCoordinateData(event, eventType)) {
          handlePress(event, eventType, mDetectedPage);
        }
        return true;
      } else {
        return mAyahSelectedListener.onClick(eventType);
      }
    }

  }

  @Override
  public HighlightingImageView getHighlightingImageView(int page) {
    if (page == mPageNumber - 1) {
      return mRightImageView;
    } else if (page == mPageNumber) {
      return mLeftImageView;
    } else {
      return null;
    }
  }

  @Override
  public void highlightAyah(int sura, int ayah, HighlightType type) {
    if (mMode == Mode.ARABIC && mCoordinateData == null) {
      new GetAyahCoordsTask(getActivity(), sura, ayah, type)
          .execute(mPageNumber - 1, mPageNumber);
    } else {
      handleHighlightAyah(sura, ayah, type);
    }
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
      HighlightingImageView hv = null;
      if (mMode == Mode.ARABIC) {
        // TODO use getHighlightingImageView?
        if (page == mPageNumber - 1) {
          hv = mRightImageView;
          // TODO unhighlight the other page?
        } else if (page == mPageNumber) {
          hv = mLeftImageView;
          // TODO unhighlight the other page?
        }
      }
      SuraAyah suraAyah = new SuraAyah(result.getSura(), result.getAyah());
      mAyahSelectedListener.onAyahSelected(eventType, suraAyah, hv);
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
}
