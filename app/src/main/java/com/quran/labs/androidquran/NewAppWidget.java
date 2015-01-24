package com.quran.labs.androidquran;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;

import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranSettings;


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
        Log.d("test","test");
    }

    @Override
    public void onDisabled(Context context) {
        Log.d("test","test");
        // Enter relevant functionality for when the last widget is disabled
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        super.onReceive(context, intent);
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId) {

//        CharSequence widgetText = context.getString(R.string.appwidget_text);
//        // Construct the RemoteViews object
//        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widgetlayout);
//        views.setTextViewText(R.id.appwidget_text, widgetText);
////        Intent intent = new Intent();
        //        intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, 50);
//        intent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, 3);
        Intent searchIntent = new Intent(context, QuranActivity.class);
        searchIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        searchIntent.setData(Uri.parse(searchIntent.toUri(Intent.URI_INTENT_SCHEME)));
        searchIntent.putExtra(QuranActivity.EXTRA_QUICK_NAVIGATE_TO_SEARCH, true);
        searchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent jumpIntent = new Intent(context, QuranActivity.class);
        jumpIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        jumpIntent.setData(Uri.parse(jumpIntent.toUri(Intent.URI_INTENT_SCHEME)));
        jumpIntent.putExtra(QuranActivity.EXTRA_QUICK_JUMP, true);
        jumpIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent lastPageIntent = new Intent(context, PagerActivity.class);
        lastPageIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        lastPageIntent.setData(Uri.parse(lastPageIntent.toUri(Intent.URI_INTENT_SCHEME)));
        lastPageIntent.putExtra("page", QuranSettings.getLastPage(context));
        int page=QuranSettings.getLastPage(context);
//        lastPageIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        lastPageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);


        PendingIntent searchPending = PendingIntent.getActivity(context, 0,
                searchIntent, 0);
        PendingIntent jumpPending = PendingIntent.getActivity(context, 0,
                jumpIntent, 0);
        PendingIntent lastPagePending = PendingIntent.getActivity(context.getApplicationContext(), (int) System.currentTimeMillis(), lastPageIntent,
                PendingIntent.FLAG_ONE_SHOT);


        RemoteViews views = new RemoteViews(context.getPackageName(),
                R.layout.widgetlayout);
        views.setOnClickPendingIntent(R.id.quickSearch, searchPending);
        views.setOnClickPendingIntent(R.id.quickLastPage, lastPagePending);
        views.setOnClickPendingIntent(R.id.quickJump, jumpPending);

        appWidgetManager.updateAppWidget(appWidgetId,views);
//        Toast.makeText(context, "widget added", Toast.LENGTH_SHORT).show();
    }
public static void updateWidgetLastPage(Context context)
{

    AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
//    RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widgetlayout);
//    ComponentName thisWidget = new ComponentName(context, NewAppWidget.class);
////       remoteViews.setTextViewText(R.id.jump, "myText" + System.currentTimeMillis());
//    Intent lastPageIntent = new Intent(context, PagerActivity.class);
//
//
//    lastPageIntent.putExtra("page", QuranSettings.getLastPage(context));
//
//    lastPageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//
//    PendingIntent lastPagePending = PendingIntent.getActivity(context, 0,
//            lastPageIntent, 0);
//    remoteViews.setOnClickPendingIntent(R.id.quickLastPage, lastPagePending);
//    appWidgetManager.updateAppWidget(thisWidget, remoteViews);
//    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetManager.getAppWidgetIds(thisWidget), R.id.quickLastPage);
//       AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
    int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, NewAppWidget.class));
    if (appWidgetIds.length > 0) {
        new NewAppWidget().onUpdate(context, appWidgetManager, appWidgetIds);
    }
}
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }
}


