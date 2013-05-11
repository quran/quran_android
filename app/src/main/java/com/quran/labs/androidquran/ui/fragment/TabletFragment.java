package com.quran.labs.androidquran.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.*;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.HighlightingImageView;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.List;
import java.util.Map;

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
   private List<Map<String, List<AyahBounds>>> mCoordinateData;
   private PaintDrawable mLeftGradient, mRightGradient = null;
   private TranslationView mLeftTranslation, mRightTranslation = null;
   private HighlightingImageView mLeftImageView, mRightImageView = null;

   public static TabletFragment newInstance(int firstPage, int mode){
      final TabletFragment f = new TabletFragment();
      final Bundle args = new Bundle();
      args.putInt(FIRST_PAGE_EXTRA, firstPage);
      args.putInt(MODE_EXTRA, mode);
      f.setArguments(args);
      return f;
   }

   @Override
   public void onCreate(Bundle savedInstanceState){
      super.onCreate(savedInstanceState);
      mPageNumber = getArguments() != null?
              getArguments().getInt(FIRST_PAGE_EXTRA) : -1;
      int width = getActivity().getWindowManager()
              .getDefaultDisplay().getWidth() / 2;
      mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      setHasOptionsMenu(true);
   }

   @Override
   public View onCreateView(LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState){
      final View view = inflater.inflate(R.layout.tablet_layout,
              container, false);
      int leftBorderImageId = R.drawable.border_left;
      int rightBorderImageId = R.drawable.border_right;
      int lineImageId = R.drawable.dark_line;

      SharedPreferences prefs =
              PreferenceManager.getDefaultSharedPreferences(getActivity());

      Resources res = getResources();

      View leftArea = view.findViewById(R.id.left_page_area);
      View rightArea = view.findViewById(R.id.right_page_area);

      if (!prefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true)) {
         int color = res.getColor(R.color.page_background);
         leftArea.setBackgroundColor(color);
         rightArea.setBackgroundColor(color);
      }
      else {
         leftArea.setBackgroundDrawable(mLeftGradient);
         rightArea.setBackgroundDrawable(mRightGradient);
      }

      boolean nightMode = false;
      int nightModeTextBrightness =
              Constants.DEFAULT_NIGHT_MODE_TEXT_BRIGHTNESS;
      if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)){
         leftBorderImageId = R.drawable.night_left_border;
         rightBorderImageId = R.drawable.night_right_border;
         lineImageId = R.drawable.light_line;
         leftArea.setBackgroundColor(Color.BLACK);
         rightArea.setBackgroundColor(Color.BLACK);
         nightMode = true;
         nightModeTextBrightness =
                 QuranSettings.getNightModeTextBrightness(getActivity());
      }

      ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
      ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
      leftBorder.setBackgroundResource(leftBorderImageId);
      rightBorder.setBackgroundResource(rightBorderImageId);

      ImageView line = (ImageView)view.findViewById(R.id.line);
      line.setImageResource(lineImageId);

      mLeftImageView = (HighlightingImageView)view
              .findViewById(R.id.left_page_image);
      mRightImageView = (HighlightingImageView)view
              .findViewById(R.id.right_page_image);
      mLeftTranslation = (TranslationView)view
              .findViewById(R.id.left_page_translation);
      mRightTranslation = (TranslationView)view
              .findViewById(R.id.right_page_translation);

      mMode = getArguments().getInt(MODE_EXTRA, Mode.ARABIC);
      if (mMode == Mode.ARABIC){
         mLeftTranslation.setVisibility(View.GONE);
         mRightTranslation.setVisibility(View.GONE);

         mLeftImageView.setVisibility(View.VISIBLE);
         mRightImageView.setVisibility(View.VISIBLE);

         mLeftImageView.setNightMode(nightMode);
         mRightImageView.setNightMode(nightMode);
         if (nightMode) {
            mLeftImageView.setNightModeTextBrightness(nightModeTextBrightness);
            mRightImageView.setNightModeTextBrightness(nightModeTextBrightness);
         }

         mLeftImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               PagerActivity pagerActivity = ((PagerActivity)getActivity());
               if (pagerActivity != null){
                  pagerActivity.toggleActionBar();
               }
            }
         });

         mRightImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               PagerActivity pagerActivity = ((PagerActivity)getActivity());
               if (pagerActivity != null){
                  pagerActivity.toggleActionBar();
               }
            }
         });
      }
      else if (mMode == Mode.TRANSLATION){
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

      mLastHighlightedPage = 0;
      mOverlayText = prefs.getBoolean(Constants.PREF_OVERLAY_PAGE_INFO, true);
      return view;
   }

   @Override
   public void onActivityCreated(Bundle savedInstanceState){
      super.onActivityCreated(savedInstanceState);

      Context context = getActivity();
      if (mMode == Mode.ARABIC){
         if (PagerActivity.class.isInstance(getActivity())){
            QuranPageWorker worker =
                    ((PagerActivity)getActivity()).getQuranPageWorker();
            worker.loadPage(mPageNumber-1, mRightImageView);
            worker.loadPage(mPageNumber, mLeftImageView);
         }

         new QueryPageCoordinatesTask(context)
                 .execute(mPageNumber - 1, mPageNumber);
      }
      else if (mMode == Mode.TRANSLATION){
         if (context != null){
            SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(context);
            String database = prefs.getString(
                    Constants.PREF_ACTIVE_TRANSLATION, null);

            new TranslationTask(context, mPageNumber-1, 0,
                    database, mRightTranslation).execute();
            new TranslationTask(context, mPageNumber, 0,
                    database, mLeftTranslation).execute();
         }
      }
   }

   public void cleanup(){
      android.util.Log.d(TAG, "cleaning up page " + mPageNumber);
      if (mLeftImageView != null){
         mLeftImageView.setImageDrawable(null);
         mLeftImageView = null;
      }

      if (mRightImageView != null){
         mRightImageView.setImageDrawable(null);
         mRightImageView = null;
      }
   }

   private class QueryPageCoordinatesTask extends QueryPageCoordsTask {
      public QueryPageCoordinatesTask(Context context){
         super(context);
      }

      @Override
      protected void onPostExecute(Rect[] rect) {
         if (rect != null){
            if (mMode == Mode.ARABIC && rect.length == 2){
               if (mRightImageView != null && mLeftImageView != null){
                  mRightImageView.setPageBounds(rect[0]);
                  mLeftImageView.setPageBounds(rect[1]);
                  if (mOverlayText){
                     mRightImageView.setOverlayText(mPageNumber-1, true);
                     mLeftImageView.setOverlayText(mPageNumber, true);
                  }
               }
            }
         }
      }
   }

   private class GetAyahCoordsTask extends QueryAyahCoordsTask {

      public GetAyahCoordsTask(Context context, MotionEvent event){
         super(context, event);
      }

      public GetAyahCoordsTask(Context context, int sura, int ayah){
         super(context, sura, ayah);
      }

      @Override
      protected void onPostExecute(List<Map<String, List<AyahBounds>>> maps){
         if (maps != null && maps.size() > 0){
            if (mMode == Mode.ARABIC && maps.size() == 2){
               mRightImageView.setCoordinateData(maps.get(0));
               mLeftImageView.setCoordinateData(maps.get(1));

               mCoordinateData = maps;

               if (mHighlightAyah){
                  handleHighlightAyah(mSura, mAyah);
               }
               // else { handleLongPress(mEvent); }
            }
         }
      }
   }

   @Override
   public void highlightAyah(int sura, int ayah){
      if (mCoordinateData == null){
         new GetAyahCoordsTask(getActivity(), sura, ayah)
                 .execute(mPageNumber - 1, mPageNumber);
      }
      else { handleHighlightAyah(sura, ayah); }
   }

   private void handleHighlightAyah(int sura, int ayah){
      if (mMode == Mode.ARABIC){
         if (mLeftImageView == null || mRightImageView == null){ return; }

         int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
         if (page == mPageNumber - 1){
            mRightImageView.highlightAyah(sura, ayah);
            mRightImageView.invalidate();
            if (mLastHighlightedPage == mPageNumber){
               mLeftImageView.unhighlight();
            }
         }
         else if (page == mPageNumber){
            mLeftImageView.highlightAyah(sura, ayah);
            mLeftImageView.invalidate();
            if (mLastHighlightedPage == mPageNumber-1){
               mRightImageView.unhighlight();
            }
         }
         mLastHighlightedPage = page;
      }
   }

   @Override
   public void unHighlightAyat(){
      if (mMode == Mode.ARABIC){
         if (mLeftImageView == null || mRightImageView == null){ return; }
         mLeftImageView.unhighlight();
         mRightImageView.unhighlight();
      }
   }
}
