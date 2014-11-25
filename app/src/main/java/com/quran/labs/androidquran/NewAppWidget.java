package com.quran.labs.androidquran;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.quran.labs.androidquran.ui.PagerActivity;


/**
 * Implementation of App Widget functionality.
 */
public class NewAppWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        final int N = appWidgetIds.length;
        for (int i=0; i<N; i++) {
            updateAppWidget(context, appWidgetManager, appWidgetIds[i]);
        }
    }



    @Override
    public void onEnabled(Context context) {
//        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
//        CharSequence widgetText = context.getString(R.string.appwidget_text);
//        // Construct the RemoteViews object
//        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetlayout);
//        views.setTextViewText(R.id.appwidget_text, widgetText);
//        Intent intent = new Intent();
//        intent.setClassName("com.quran.labs.androidquran", "com.quran.labs.androidquran.PagerActivity");
//        intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, 1);
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        PendingIntent myPI = PendingIntent.getService(context, 0, intent, 0);
//        views.setOnClickPendingIntent(R.id.navigate, myPI);
//        ComponentName comp = new ComponentName(context.getPackageName(), NewAppWidget.class.getName());
//        // Instruct the widget manager to update the widget
//        mgr.updateAppWidget(comp, views);
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId) {

//        CharSequence widgetText = context.getString(R.string.appwidget_text);
//        // Construct the RemoteViews object
//        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetlayout);
//        views.setTextViewText(R.id.appwidget_text, widgetText);
////        Intent intent = new Intent();
        Intent intent = new Intent(context, PagerActivity.class);
//        intent.setClassName("com.quran.labs.androidquran", "com.quran.labs.androidquran.PagerActivity");
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
        intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, 50);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        PendingIntent myPI = PendingIntent.getService(context, 0, intent,PendingIntent.FLAG_UPDATE_CURRENT);
//        views.setOnClickPendingIntent(R.id.navigate, myPI);
//        ComponentName comp = new ComponentName(context.getPackageName(), NewAppWidget.class.getName());
//        // Instruct the widget manager to update the widget
//        appWidgetManager.updateAppWidget(appWidgetId, views);
        String url = "http://www.tutorialspoint.com";
//        Intent intent = new Intent(Intent.ACTION_VIEW);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.setData(Uri.parse(url));
        PendingIntent pending = PendingIntent.getActivity(context, 0,
                intent, 0);
        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widgetlayout);
        views.setOnClickPendingIntent(R.id.navigate, pending);
        appWidgetManager.updateAppWidget(appWidgetId,views);
        Toast.makeText(context, "widget added", Toast.LENGTH_SHORT).show();
    }
}


