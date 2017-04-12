package com.quran.labs.androidquran.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.Bookmark;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.database.BookmarksDBAdapter;

import java.util.ArrayList;
import java.util.List;

public class WidgetListProvider implements RemoteViewsService.RemoteViewsFactory {
  private ArrayList<ItemList> listItemList = new ArrayList();
  private Context context = null;
  private int appWidgetId;

  public WidgetListProvider(Context context, Intent intent) {
    this.context = context;
    appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID);
    populateListItem();
  }

  public void populateListItem() {
    Context appContext = context.getApplicationContext();
    BookmarksDBAdapter a = new BookmarksDBAdapter(appContext);
    List<Bookmark> bookmarksList = a.getBookmarks(0);// 0 = date added
    listItemList = new ArrayList();
    for (int i = 0; i < bookmarksList.size(); i++) {
      Bookmark bm = bookmarksList.get(i);
      ItemList item = new ItemList();
      item.sura = QuranInfo.getSuraNameFromPage(appContext, bm.page);
      item.page = bm.page;
      listItemList.add(item);
    }


  }

  @Override
  public void onCreate() {
  }

  @Override
  public void onDataSetChanged() {
    populateListItem();

  }

  @Override
  public void onDestroy() {
  }

  @Override
  public int getCount() {
    return listItemList.size();
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public boolean hasStableIds() {
    return false;
  }

  /*
  *Similar to getView of Adapter where instead of View
  *we return RemoteViews
  *
  */
  @Override
  public RemoteViews getViewAt(int position) {
    final RemoteViews remoteView = new RemoteViews(
        context.getPackageName(), R.layout.bookmarks_widget_list_row);
    ItemList item = listItemList.get(position);
    remoteView.setTextViewText(R.id.Suratitle, item.sura + "");
    remoteView.setTextViewText(R.id.Surametadata, context.getResources().getText(R.string.quran_page) + " " + item.page);
    remoteView.setImageViewResource(R.id.WidgetFavIcon, R.drawable.ic_favorite);
    Bundle extras = new Bundle();
    extras.putInt("page", item.page);
    Intent fillInIntent = new Intent();
    fillInIntent.putExtras(extras);
    remoteView.setOnClickFillInIntent(R.id.widget_item, fillInIntent);
    return remoteView;
  }

  @Override
  public RemoteViews getLoadingView() {
    return null;
  }

  @Override
  public int getViewTypeCount() {
    return 1;
  }


  static class BookmarksTable {
    static final String TABLE_NAME = "bookmarks";
    static final String ID = "_ID";
    static final String SURA = "sura";
    static final String AYAH = "ayah";
    static final String PAGE = "page";
    static final String ADDED_DATE = "added_date";
  }

  static class ItemList {
    public String sura;
    public int page;
  }
}
