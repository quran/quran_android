package com.quran.labs.androidquran.ui.helpers;

import java.util.List;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;
import com.quran.labs.androidquran.database.BookmarksDBAdapter.Bookmark;

public class ShowBookmarkListTask extends AsyncTask<Integer, Void, List<Bookmark>> {
   private Context cx;
   private OnBookmarkSelectedListener listener;
   
   public ShowBookmarkListTask(Context cx, OnBookmarkSelectedListener listener) {
      this.cx = cx;
      this.listener = listener;
   }
   
   @Override
   protected List<Bookmark> doInBackground(Integer... params) {
      List<Bookmark> result = null;
      BookmarksDBAdapter dba = new BookmarksDBAdapter(cx);
      dba.open();
      result = dba.getBookmarksAsList();
      dba.close();
      return result;
   }
   
   @Override
   protected void onPostExecute(List<Bookmark> result) {
      if (result != null){
         final Dialog dlg = new Dialog(cx);
         dlg.setTitle(cx.getString(R.string.bookmarks_list_title));
         dlg.setContentView(R.layout.bookmarks_list);
         
         final ListAdapter adapter = new ArrayAdapter<Bookmark>(
               cx, R.layout.bookmark_row, R.id.bookmark_text, result);
         
         ListView lv = (ListView) dlg.findViewById(R.id.bookmarks_list);
         lv.setAdapter(adapter);
         lv.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
               Bookmark bookmark = (Bookmark) adapter.getItem(position);
               if (listener != null)
                  listener.onBookmarkSelected(bookmark);
               dlg.dismiss();
            }
         });
         TextView emptyTextView = (TextView)dlg.findViewById(R.id.empty_bookmarks_list);
         lv.setEmptyView(emptyTextView);
         
         int background = cx.getResources().getColor(R.color.transparent_dialog_color);
         dlg.getWindow().setBackgroundDrawable(new ColorDrawable(background));
         dlg.show();
      }
   }
   
   public interface OnBookmarkSelectedListener {
      public void onBookmarkSelected(Bookmark bookmark);
   }
}

