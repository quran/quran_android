package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import com.actionbarsherlock.app.SherlockDialogFragment;
import com.quran.labs.androidquran.R;

import java.util.ArrayList;
import java.util.List;

import static com.quran.labs.androidquran.database.BookmarksDBAdapter.BookmarkCategory;

public class BookmarkDialog extends SherlockDialogFragment {

   private Integer mSura;
   private Integer mAyah;
   private int mPage = -1;
   private List<BookmarkCategory> mCategories;

   private ListView mListView;

   private static final String PAGE = "page";
   private static final String SURA = "sura";
   private static final String AYAH = "ayah";

   public BookmarkDialog(Integer sura, Integer ayah, int page,
                         List<BookmarkCategory> categories){
      mSura = sura;
      mAyah = ayah;
      mPage = page;

      mCategories = categories;
   }

   public BookmarkDialog(){
   }

   @Override
   public void onSaveInstanceState(Bundle outState) {
      outState.putInt(PAGE, mPage);
      outState.putInt(SURA, mSura == null? 0 : mSura);
      outState.putInt(AYAH, mAyah == null? 0 : mAyah);
      super.onSaveInstanceState(outState);
   }

   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState) {
      FragmentActivity activity = getActivity();

      if (savedInstanceState != null){
         mSura = savedInstanceState.getInt(SURA);
         mAyah = savedInstanceState.getInt(AYAH);
         mPage = savedInstanceState.getInt(PAGE);

         if (mSura == 0){ mSura = null; }
         if (mAyah == 0){ mAyah = null; }
      }

      if (mCategories == null){
         mCategories = new ArrayList<BookmarkCategory>();
      }

      mCategories.add(new BookmarkCategory(0,
         getString(R.string.sample_bookmark_uncategorized), null));

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      final ListAdapter adapter = new ArrayAdapter<BookmarkCategory>(
              activity, R.layout.bookmark_row,
              R.id.bookmark_text, mCategories);

      mListView = new ListView(activity);
      mListView.setAdapter(adapter);
      mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view,
                                 int position, long id) {
            BookmarkCategory category =
                    (BookmarkCategory)adapter.getItem(position);
            Activity currentActivity = getActivity();
            if (currentActivity != null &&
                    currentActivity instanceof OnCategorySelectedListener){
               ((OnCategorySelectedListener)currentActivity)
                       .onCategorySelected(category, mSura, mAyah, mPage);
            }
            BookmarkDialog.this.dismiss();
         }
      });

      builder.setView(mListView);
      return builder.create();
   }

   public interface OnCategorySelectedListener {
      public void onCategorySelected(BookmarkCategory bookmark,
                                     Integer sura, Integer ayah, int page);
   }
}
