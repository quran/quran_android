package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import com.actionbarsherlock.app.SherlockDialogFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import java.util.ArrayList;
import java.util.List;

import static com.quran.labs.androidquran.database.BookmarksDBAdapter.BookmarkCategory;

public class BookmarkDialog extends SherlockDialogFragment {

   private Integer mSura;
   private Integer mAyah;
   private int mPage = -1;
   private List<BookmarkCategory> mCategories;
   private ArrayAdapter mAdapter;

   private ListView mListView;

   private static final String PAGE = "page";
   private static final String SURA = "sura";
   private static final String AYAH = "ayah";

   public BookmarkDialog(Integer sura, Integer ayah, int page){
      mSura = sura;
      mAyah = ayah;
      mPage = page;
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

   public void requestCategoryData(){
      new Thread(new Runnable() {
         @Override
         public void run() {
            Activity activity = getActivity();
            if (activity == null){ return; }

            BookmarksDBAdapter dba =
                    new BookmarksDBAdapter(activity);
            dba.open();
            final List<BookmarkCategory> categories = dba.getCategories();
            dba.close();

            categories.add(new BookmarkCategory(0,
                    getString(R.string.sample_bookmark_uncategorized), null));
            mCategories = categories;
            mAdapter.addAll(mCategories);
            mAdapter.notifyDataSetChanged();
         }
      }).start();
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

      mCategories = new ArrayList<BookmarkCategory>();

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      mAdapter = new ArrayAdapter<BookmarkCategory>(
              activity, R.layout.bookmark_row,
              R.id.bookmark_text, mCategories);

      mListView = new ListView(activity);
      mListView.setAdapter(mAdapter);
      mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view,
                                 int position, long id) {
            BookmarkCategory category =
                    (BookmarkCategory)mAdapter.getItem(position);
            Activity currentActivity = getActivity();
            if (currentActivity != null &&
                    currentActivity instanceof OnCategorySelectedListener){
               ((OnCategorySelectedListener)currentActivity)
                       .onCategorySelected(category, mSura, mAyah, mPage);
            }
            BookmarkDialog.this.dismiss();
         }
      });

      requestCategoryData();
      builder.setView(mListView);
      return builder.create();
   }

   public interface OnCategorySelectedListener {
      public void onCategorySelected(BookmarkCategory bookmark,
                                     Integer sura, Integer ayah, int page);
   }
}
