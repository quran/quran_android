package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranDataProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.util.QuranSettings;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.ArrayList;
import java.util.List;

public class TranslationFragment extends SherlockFragment {
   private static final String TAG = "TranslationPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
   private boolean mIsPaused;
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
      if (Build.VERSION.SDK_INT >= 11){
         mTranslationView.setOnTextClickedListener(
                 new TranslationView.OnTextClickedListener() {
            @Override
            public void onTextClicked() {
               ((PagerActivity) getActivity()).toggleActionBar();
            }
         });
      }
      else {
         mTranslationView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               ((PagerActivity)getActivity()).toggleActionBar();
            }
         });
      }

      String database = prefs.getString(
              Constants.PREF_ACTIVE_TRANSLATION, null);
      refresh(database);
      return view;
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
         new TranslationTask(database).execute(mPageNumber);
      }
   }

   class TranslationTask extends AsyncTask<Integer, Void, List<QuranAyah>> {
      private String mDatabaseName = null;

      public TranslationTask(String databaseName){
         mDatabaseName = databaseName;
         Activity activity = getActivity();
         if (activity != null && activity instanceof PagerActivity){
            ((PagerActivity)activity).setLoadingIfPage(mPageNumber);
         }
      }

      @Override
      protected List<QuranAyah> doInBackground(Integer... params) {
         Activity activity = getActivity();
         if (activity == null){ return null; }

         int page = params[0];
         Integer[] bounds = QuranInfo.getPageBounds(page);
         if (bounds == null){ return null; }

         String databaseName = mDatabaseName;
         List<QuranAyah> verses = new ArrayList<QuranAyah>();

         try {
            DatabaseHandler translationHandler =
                    new DatabaseHandler(databaseName);
            Cursor translationCursor =
                    translationHandler.getVerses(bounds[0], bounds[1],
                            bounds[2], bounds[3],
                            DatabaseHandler.VERSE_TABLE);

            DatabaseHandler ayahHandler = null;
            Cursor ayahCursor = null;

            if (QuranSettings.wantArabicInTranslationView(activity)){
               try {
                  ayahHandler = new DatabaseHandler(
                       QuranDataProvider.QURAN_ARABIC_DATABASE);
                  ayahCursor = ayahHandler.getVerses(bounds[0], bounds[1],
                       bounds[2], bounds[3],
                       DatabaseHandler.ARABIC_TEXT_TABLE);
               }
               catch (Exception e){
                  // ignore any exceptions due to no arabic database
               }
            }

            if (translationCursor != null) {
               boolean validAyahCursor = false;
               if (ayahCursor != null && ayahCursor.moveToFirst()){
                  validAyahCursor = true;
               }

               if (translationCursor.moveToFirst()) {
                  do {
                     int sura = translationCursor.getInt(0);
                     int ayah = translationCursor.getInt(1);
                     String translation = translationCursor.getString(2);
                     QuranAyah verse = new QuranAyah(sura, ayah);
                     verse.setTranslation(translation);
                     if (validAyahCursor){
                        String text = ayahCursor.getString(2);
                        verse.setText(text);
                     }
                     verses.add(verse);
                  }
                  while (translationCursor.moveToNext() &&
                          (!validAyahCursor || ayahCursor.moveToNext()));
               }
               translationCursor.close();
               if (ayahCursor != null){
                  ayahCursor.close();
               }
            }
            translationHandler.closeDatabase();
            if (ayahHandler != null){
               ayahHandler.closeDatabase();
            }
         }
         catch (Exception e){
            Log.d(TAG, "unable to open " + databaseName + " - " + e);
         }

         return verses;
      }

      @Override
      protected void onPostExecute(List<QuranAyah> result) {
         if (result != null){
            mTranslationView.setAyahs(result);
         }

         Activity activity = getActivity();
         if (activity != null && activity instanceof PagerActivity){
            ((PagerActivity)activity).setLoading(false);
         }
      }
   }
}
