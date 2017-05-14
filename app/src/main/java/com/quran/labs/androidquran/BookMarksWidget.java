package com.quran.labs.androidquran;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.widgets.WidgetService;



/**
 * Implementation of App Widget functionality.
 */
public class BookMarksWidget extends AppWidgetProvider {


  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {

int page=1;

    final int N = appWidgetIds.length;
    for (int i = 0; i < N; ++i) {

      Intent svcIntent=new Intent(context, WidgetService.class);

      svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetIds[i]);
      svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));

      RemoteViews widget=new RemoteViews(context.getPackageName(),
          R.layout.book_marks_widget);

      Intent intent = new Intent(context, QuranDataActivity.class);
      PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.WidgetIconButton, pendingIntent);

      intent = new Intent(context, SearchActivity.class);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.WidgetBtnSearch, pendingIntent);

      intent = new Intent(context, PagerActivity.class);
      pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
      widget.setOnClickPendingIntent(R.id.WidgetBtnGoToQuran, pendingIntent);


      widget.setRemoteAdapter(appWidgetIds[i], R.id.listViewWidget,
          svcIntent);

      Intent clickIntent=new Intent(context, PagerActivity.class);
      PendingIntent clickPI=PendingIntent
          .getActivity(context, 0,
              clickIntent,
              PendingIntent.FLAG_UPDATE_CURRENT);

      widget.setPendingIntentTemplate(R.id.listViewWidget, clickPI);

      widget.setEmptyView(R.id.listViewWidget, R.id.empty_view);

      appWidgetManager.updateAppWidget(appWidgetIds[i], widget);




    }
    super.onUpdate(context, appWidgetManager, appWidgetIds);
  }


  @Override
  public void onEnabled(Context context) {

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

