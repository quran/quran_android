package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;

public  class VoiceCommandsUtil {

  private final Context myContext;

  public VoiceCommandsUtil(Context cont) {
    myContext = cont;
  }

  public boolean findCommand(String S, QuranActivity act) {

    int n = 0;
    int check = 1;
    int sura = 1;
    int ayah = 1;
    int page = 1;
    boolean checkStart = false;
    final String[] startName = myContext.getResources().getStringArray(R.array.voiceCommandsList);

    final String language = QuranSettings.getInstance(myContext).isArabicNames() ? "ar-SA" : "en-US";

    //Function to find a similar command from the array list of commands
    while (check != 0) {
      if(S.compareTo(startName[n]) == 0)
          check = 0;
      n++;
      if (n == startName.length)
        break;
    }

    //Do something with the command
    Intent i;
    if (check == 0) {
      switch (n) {
        case 1:
          i = new Intent(myContext, QuranPreferenceActivity.class);
          myContext.startActivity(i);
          return true;
        case 2:
          act.jumpToLastPage();
          return true;
        case 3:
          i = new Intent(myContext, HelpActivity.class);
          myContext.startActivity(i);
          return true;
        case 4:
          i = new Intent(myContext, AboutUsActivity.class);
          myContext.startActivity(i);
          return true;
        case 5:
          sura = 2;
          page = QuranInfo.getPageFromSuraAyah(sura, ayah);
          i = new Intent(myContext, PagerActivity.class);
          i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
          i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
          i.putExtra("page", page);
          myContext.startActivity(i);
          return true;
        case 6:
          sura = 114;
          page = QuranInfo.getPageFromSuraAyah(sura, ayah);
          i = new Intent(myContext, PagerActivity.class);
          i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
          i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
          i.putExtra("page", page);
          myContext.startActivity(i);
          return true;
        default:
          return false;
      }
    }
    //Command cannot be found
    else {
      CharSequence text = "";
      String notFound = myContext.getText(R.string.commandNotFound).toString();
      text = notFound + S;
      int duration = Toast.LENGTH_LONG;
      Toast toast = Toast.makeText(myContext.getApplicationContext(), text, duration);
      toast.show();
      return false;
    }
  }
}

