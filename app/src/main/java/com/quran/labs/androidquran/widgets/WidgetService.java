package com.quran.labs.androidquran.widgets;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.widget.RemoteViewsService;

/**
 * Created by adnan on 4/1/2017.
 */

public class WidgetService extends RemoteViewsService {
/*
* So pretty simple just defining the Adapter of the listview
* here Adapter is ListProvider
* */

  @Override
  public RemoteViewsFactory onGetViewFactory(Intent intent) {
    int appWidgetId = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID);

    return (new WidgetListProvider(this.getApplicationContext(), intent));
  }



}
