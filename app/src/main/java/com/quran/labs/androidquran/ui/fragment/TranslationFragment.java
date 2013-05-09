package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.AyahTracker;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.ui.helpers.TranslationTask;
import com.quran.labs.androidquran.widgets.TranslationView;

public class TranslationFragment extends SherlockFragment
   implements AyahTracker {
   private static final String TAG = "TranslationPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private static final String SI_PAGE_NUMBER = "SI_PAGE_NUMBER";
   private static final String SI_HIGHLIGHTED_AYAH = "SI_HIGHLIGHTED_AYAH";

   private int mPageNumber;
   private boolean mIsPaused;
   private int mHighlightedAyah;
   private TranslationView mTranslationView;
   private PaintDrawable mLeftGradient, mRightGradient = null;

   public static TranslationFragment newInstance(int page){
      final TranslationFragment f = new TranslationFragment();
      final Bundle args = new Bundle();
      args.putInt(PAGE_NUMBER_EXTRA, page);
      f.setArguments(args);
      return f;
   }

   @Override
   public void onCreate(Bundle savedInstanceState){
      super.onCreate(savedInstanceState);
      mIsPaused = false;
      mPageNumber = getArguments() != null?
              getArguments().getInt(PAGE_NUMBER_EXTRA) : -1;
      if (savedInstanceState != null){
         int page = savedInstanceState.getInt(SI_PAGE_NUMBER, -1);
         if (page == mPageNumber){
            int highlightedAyah =
                    savedInstanceState.getInt(SI_HIGHLIGHTED_AYAH, -1);
            if (highlightedAyah > 0){
               mHighlightedAyah = highlightedAyah;
            }
         }
      }
      int width = getActivity().getWindowManager()
              .getDefaultDisplay().getWidth();
      mLeftGradient = QuranDisplayHelper.getPaintDrawable(width, 0);
      mRightGradient = QuranDisplayHelper.getPaintDrawable(0, width);
      setHasOptionsMenu(true);
   }

   @Override
   public View onCreateView(LayoutInflater inflater,
                            ViewGroup container, Bundle savedInstanceState){
      final View view = inflater.inflate(
              R.layout.translation_layout, container, false);
      view.setBackgroundDrawable((mPageNumber % 2 == 0?
              mLeftGradient : mRightGradient));

      SharedPreferences prefs = PreferenceManager
              .getDefaultSharedPreferences(getActivity());

      Resources res = getResources();
      if (!prefs.getBoolean(Constants.PREF_USE_NEW_BACKGROUND, true)) {
    	  view.setBackgroundColor(res.getColor(R.color.page_background));
      }
      if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)){
    	  view.setBackgroundColor(Color.BLACK);
	   }

      int lineImageId = R.drawable.dark_line;
      int leftBorderImageId = R.drawable.border_left;
      int rightBorderImageId = R.drawable.border_right;
      if (prefs.getBoolean(Constants.PREF_NIGHT_MODE, false)){
         leftBorderImageId = R.drawable.night_left_border;
         rightBorderImageId = R.drawable.night_right_border;
         lineImageId = R.drawable.light_line;
      }

      ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
      ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
      if (mPageNumber % 2 == 0){
         rightBorder.setVisibility(View.GONE);
         leftBorder.setBackgroundResource(leftBorderImageId);
      }
      else {
         rightBorder.setVisibility(View.VISIBLE);
         rightBorder.setBackgroundResource(rightBorderImageId);
         leftBorder.setBackgroundResource(lineImageId);
      }

      mTranslationView = (TranslationView)view
              .findViewById(R.id.translation_text);
      mTranslationView.setTranslationClickedListener(
              new TranslationView.TranslationClickedListener() {
         @Override
         public void onTranslationClicked() {
            ((PagerActivity) getActivity()).toggleActionBar();
         }
      });

      String database = prefs.getString(
              Constants.PREF_ACTIVE_TRANSLATION, null);
      refresh(database);
      return view;
   }

   @Override
   public void highlightAyah(int sura, int ayah){
      if (mTranslationView != null){
         mHighlightedAyah = QuranInfo.getAyahId(sura, ayah);
         mTranslationView.highlightAyah(mHighlightedAyah);
      }
   }

   @Override
   public void unHighlightAyat(){
      if (mTranslationView != null){
         mTranslationView.unhighlightAyat();
         mHighlightedAyah = -1;
      }
   }

   @Override
   public void onResume() {
      super.onResume();
      if (mIsPaused){
         mTranslationView.refresh();
      }
      mIsPaused = false;
   }

   @Override
   public void onPause() {
      mIsPaused = true;
      super.onPause();
   }

   public void refresh(String database){
      if (database != null){
         Activity activity = getActivity();
         if (activity != null){
            new TranslationTask(activity, mPageNumber,
                    mHighlightedAyah, database, mTranslationView).execute();
         }
      }
   }

   @Override
   public void onSaveInstanceState(Bundle outState) {
      if (mHighlightedAyah > 0){
         outState.putInt(SI_HIGHLIGHTED_AYAH, mHighlightedAyah);
      }
      super.onSaveInstanceState(outState);
   }
}
