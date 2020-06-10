package com.quran.labs.androidquran;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;

public class SearchWidget extends AppWidgetProvider {

  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
    // There may be multiple widgets active, so update all of them
    for (int appWidgetId : appWidgetIds) {
      RemoteViews widget = new RemoteViews(context.getPackageName(), R.layout.search_widget);

      Intent intent = new Intent(context, QuranDataActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.SearchWidgetIconButton, pendingIntent);

      intent = new Intent(context, SearchActivity.class);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.SearchWidgetBtnSearch, pendingIntent);

      intent = new Intent(context, QuranActivity.class);
      intent.setAction(ShortcutsActivity.ACTION_JUMP_TO_LATEST);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.SearchWidgetBtnGoToQuran, pendingIntent);

      appWidgetManager.updateAppWidget(appWidgetId, widget);
    }
    super.onUpdate(context, appWidgetManager, appWidgetIds);
  }
}
