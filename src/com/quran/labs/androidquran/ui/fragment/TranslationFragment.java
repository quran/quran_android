package com.quran.labs.androidquran.ui.fragment;

import android.database.Cursor;
import android.graphics.drawable.PaintDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.actionbarsherlock.app.SherlockFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.DatabaseHandler;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.helpers.QuranDisplayHelper;
import com.quran.labs.androidquran.widgets.TranslationView;

import java.util.ArrayList;
import java.util.List;

public class TranslationFragment extends SherlockFragment {
   private static final String TAG = "TranslationPageFragment";
   private static final String PAGE_NUMBER_EXTRA = "pageNumber";

   private int mPageNumber;
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

      ImageView leftBorder = (ImageView)view.findViewById(R.id.left_border);
      ImageView rightBorder = (ImageView)view.findViewById(R.id.right_border);
      if (mPageNumber % 2 == 0){
         rightBorder.setVisibility(View.GONE);
         leftBorder.setBackgroundResource(R.drawable.border_left);
      }
      else {
         rightBorder.setVisibility(View.VISIBLE);
         rightBorder.setBackgroundResource(R.drawable.border_right);
         leftBorder.setBackgroundResource(R.drawable.dark_line);
      }

      mTranslationView = (TranslationView)view
              .findViewById(R.id.translation_text);
      mTranslationView.setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            ((PagerActivity)getActivity()).toggleActionBar();
         }
      });

      new TranslationTask().execute(mPageNumber);
      return view;
   }

   class TranslationTask extends AsyncTask<Integer, Void, List<QuranAyah>> {
      @Override
      protected List<QuranAyah> doInBackground(Integer... params) {
         int page = params[0];
         Integer[] bounds = QuranInfo.getPageBounds(page);
         if (bounds == null){ return null; }

         String databaseName = "quran.ensi.db";
         List<QuranAyah> verses = new ArrayList<QuranAyah>();

         try {
            DatabaseHandler handler = new DatabaseHandler(databaseName);
            Cursor cursor =
                    handler.getVerses(bounds[0], bounds[1], bounds[2],
                            bounds[3], DatabaseHandler.VERSE_TABLE);

            if (cursor != null) {
               if (cursor.moveToFirst()) {
                  do {
                     int sura = cursor.getInt(0);
                     int ayah = cursor.getInt(1);
                     String text = cursor.getString(2);
                     QuranAyah verse = new QuranAyah(sura, ayah);
                     verse.setText(text);
                     verses.add(verse);
                  }
                  while (cursor.moveToNext());
               }
               cursor.close();
            }
            handler.closeDatabase();
         }
         catch (Exception e){
            Log.d(TAG, "unable to open " + databaseName + " - " + e);
         }

         return verses;
      }

      @Override
      protected void onPostExecute(List<QuranAyah> result) {
         mTranslationView.setAyahs(result);
      }
   }
}
