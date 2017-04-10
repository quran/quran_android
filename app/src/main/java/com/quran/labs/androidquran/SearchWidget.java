package com.quran.labs.androidquran.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.quran.labs.androidquran.BookMarksWidget;
import com.quran.labs.androidquran.QuranDataActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.SearchActivity;
import com.quran.labs.androidquran.ui.PagerActivity;


/**
 * Implementation of App Widget functionality.
 */
public class SearchWidget extends AppWidgetProvider {

  static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                              int appWidgetId) {

    //CharSequence widgetText = context.getString(R.string.search_appwidget_text);

    //RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.search_widget);
    //views.setTextViewText(R.id, widgetText);

    //appWidgetManager.updateAppWidget(appWidgetId, views);
  }

  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    // There may be multiple widgets active, so update all of them
    for (int appWidgetId : appWidgetIds) {

      RemoteViews widget=new RemoteViews(context.getPackageName(),
          R.layout.search_widget);

      Intent intent = new Intent(context, QuranDataActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.SearchWidgetIconButton, pendingIntent);

      intent = new Intent(context, SearchActivity.class);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.SearchWidgetBtnSearch, pendingIntent);

      intent = new Intent(context, PagerActivity.class);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.SearchWidgetBtnGoToQuran, pendingIntent);


      updateAppWidget(context, appWidgetManager, appWidgetId);
    }
  }

  @Override
  public void onEnabled(Context context) {
    // Enter relevant functionality for when the first widget is created
  }

  @Override
  public void onDisabled(Context context) {
    // Enter relevant functionality for when the last widget is disabled
  }
  @Override
  public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);

    updateWidget(context);

  }

  public static void updateWidget(Context context) {
    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
    int appWidgetIds[] = appWidgetManager.getAppWidgetIds(new ComponentName(context, BookMarksWidget.class));
    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds,R.id.listViewWidget);

  }
}

