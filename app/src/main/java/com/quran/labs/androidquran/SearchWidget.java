package com.quran.labs.androidquran;

import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;
import com.quran.labs.androidquran.ui.PagerActivity;

/**
 * Implementation of App Widget functionality.
 */
public class SearchWidget extends AppWidgetProvider {

  static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                              int appWidgetId) {

    CharSequence widgetText = context.getString(R.string.appwidget_text);
    // Construct the RemoteViews object
//    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.search_widget);
//    views.setTextViewText(R.id.editTextWidgetSearch, R.string.search_hint+"...");

    // Instruct the widget manager to update the widget
//    appWidgetManager.updateAppWidget(appWidgetId, views);
  }

  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    // There may be multiple widgets active, so update all of them
    for (int appWidgetId : appWidgetIds) {

      Intent intent = new Intent(context, SearchActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

      // Get the layout for the App Widget and attach an on-click listener
      // to the button
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.search_widget);
        views.setOnClickPendingIntent(R.id.textViewWidgetSearch, pendingIntent);
        // Tell the AppWidgetManager to perform an update on the current app widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
      }
//      updateAppWidget(context, appWidgetManager, appWidgetId);
    super.onUpdate(context, appWidgetManager, appWidgetIds);
    }



  @Override
  public void onEnabled(Context context) {
    // Enter relevant functionality for when the first widget is created
  }

  @Override
  public void onDisabled(Context context) {
    // Enter relevant functionality for when the last widget is disabled
  }
}

