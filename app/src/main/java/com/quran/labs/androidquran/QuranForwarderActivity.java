package com.quran.labs.androidquran;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.quran.data.core.QuranInfo;
import com.quran.labs.androidquran.ui.PagerActivity;

import javax.inject.Inject;

public class QuranForwarderActivity extends Activity {
  @Inject QuranInfo quranInfo;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      ((QuranApplication) getApplication()).getApplicationComponent().inject(this);

      // handle urls of type quran://sura/ayah
      Intent intent = getIntent();
      if (intent != null){
         Uri data = intent.getData();
         if (data != null){
            String urlString = data.toString();
            String[] pieces = urlString.split("/", -1);

            Integer sura = null;
            int ayah = 1;
            for (String s : pieces){
               try {
                  int i = Integer.parseInt(s);
                  if (sura == null){ sura = i; }
                  else { ayah = i; break; }
               }
               catch (NumberFormatException nfe){
                 // leave it as null
               }
            }

            if (sura != null){
               int page = quranInfo.getPageFromSuraAyah(sura, ayah);
               Intent showSuraIntent = new Intent(this, PagerActivity.class);
               showSuraIntent.putExtra("page", page);
               showSuraIntent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
               showSuraIntent.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
               startActivity(showSuraIntent);
            }
         }
      }
      finish();
   }
}
