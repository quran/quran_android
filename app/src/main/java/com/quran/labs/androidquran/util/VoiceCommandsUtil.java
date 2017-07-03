package com.quran.labs.androidquran.util;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.quran.labs.androidquran.AboutUsActivity;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.HelpActivity;
import com.quran.labs.androidquran.QuranPreferenceActivity;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;

import java.util.List;

//This is the main class that will compare the spoken text and find a match
//It will take an action based on the result
public  class VoiceCommandsUtil {

  private final Context myContext;
  private QuranSettings mQuranSettings;
  private static String languageCode = "en-US";
  private static String[] commandsList;
  private static String[] suratList;
  private static String[] languageList;

  final static class LookAheadLanguage {
    static final int ENGLISH = 1;
    static final int ARABIC = 2;

    // make sure to update these when a lookup type is added
    static final int MIN = 1;
    static final int MAX = 2;
  }

  public VoiceCommandsUtil(Context context) {
    myContext = context;
  }

  //Function to find the language code. Additional languages can be added here
  public String findLanguageCode(int languagePrefs) {
    switch (languagePrefs) {
      case 1:
        languageCode = myContext.getResources().getString(R.string.english_code);
        commandsList = myContext.getResources().getStringArray(R.array.voice_Commands_List_EN);
        suratList = myContext.getResources().getStringArray(R.array.sura_List_Translated_EN);
        languageList = myContext.getResources().getStringArray(R.array.language_List_EN);
        return languageCode;
      case 2:
        languageCode = myContext.getResources().getString(R.string.arabic_code);
        commandsList = myContext.getResources().getStringArray(R.array.voice_Commands_List_AR);
        suratList = myContext.getResources().getStringArray(R.array.sura_List_AR);
        languageList = myContext.getResources().getStringArray(R.array.language_List_AR);
        return languageCode;
      default:
        languageCode = myContext.getResources().getString(R.string.english_code);
        commandsList = myContext.getResources().getStringArray(R.array.voice_Commands_List_EN);
        suratList = myContext.getResources().getStringArray(R.array.sura_List_Translated_EN);
        languageList = myContext.getResources().getStringArray(R.array.language_List_EN);
        return languageCode;
    }
  }

  public boolean findCommand(List<String> results, QuranActivity quranActivity) {

    int n = 0;     //total number of commands in voice_commands.xml
    boolean check = false; //used for comparing spokenText and the list of commands
    int sura = 1;
    int ayah = 1;
    int page = 1;
    int language = 1; //language counter as per LookAheadLanguage class
    String[] spokenText = results.toArray(new String[0]);

    mQuranSettings = QuranSettings.getInstance(myContext);
    mQuranSettings.getPreferredVoiceLanguage();
    //Check if this is an english language then change all letters to lowercase
    if (languageCode.substring(0,2).compareTo("en")== 0){
      int k = 0;
      language = 1;
      while(k != spokenText.length){
        spokenText[k] = spokenText[k].toLowerCase();
        k++;
      }
    }
    String[] separatedText = spokenText[0].split(" ");
    //Function to find an exact command from the array list of commands
    while (n != commandsList.length){
      if(separatedText[0].compareTo(commandsList[n]) == 0) {
        break;
      }
      n++;
    }
    //This jumps to the selected page
    if(n == 1){
      separatedText = spokenText[0].split(" ");
      try {
        int myNum = Integer.parseInt(separatedText[separatedText.length - 1]);
        page = myNum;
        check = true;
      } catch(NumberFormatException nfe) {
      }
    }
    //This searches for the required Surah
    if(n == 4){
      int m = 0;
      int j = 0;
      while (j != spokenText.length) {
        separatedText = spokenText[j].split(" ");
        while (m != suratList.length){
          if (separatedText[separatedText.length - 1].compareTo(suratList[m]) == 0) {
            check = true;
            sura = m + 1;
            //Special cases for english language due to similar last word sura names
            //First case is for "Those who set the Ranks" and "The Ranks"
            //Second case is for "The Enshrouded One" and "The Cloaked One"
            if (language == 1){
              if (m == 36){
                if ((separatedText[separatedText.length - 3]).compareTo("set") == 0){
                  sura = 37;
                } else {
                  sura = 61;
                }
              } else if (m == 72){
                if ((separatedText[separatedText.length - 3]).compareTo("cloaked") == 0){
                  sura = 74;
                } else {
                  sura = 73;
                }
              }
            }
            break;
          }
          m++;
        }
        m = 0;
        j++;
        if(check)
          break;
      }
      if (m == suratList.length && j == spokenText.length)
        check = false;
    }
    //This searches for the required language
    if(n == 5){
      int m = 0;
      while (m != languageList.length){
        if(separatedText[1].compareTo(languageList[m]) == 0) {
          check = true;
          language = m+1;
          break;
        }
        m++;
      }
      if (m == languageList.length)
        check = false;
    }
    //Do something with the command. This can be expanded to add additional features to voice commands
    //by adding the commands to voice_commands.xml
    //The order of the cases is as per the order the of array in the voice_commands.xml file
    //If the function returns false then the command could not be executed.
    Intent i;
    if (check) {
      switch (n) {
        //Command "settings" -> Open settings page
        case 0:
          i = new Intent(myContext, QuranPreferenceActivity.class);
          myContext.startActivity(i);
          return true;
        //Command "Page number..." -> Jump to selected page
        case 1:
          quranActivity.jumpTo(page);
          return true;
        //Command "help" -> Open help page
        case 2:
          i = new Intent(myContext, HelpActivity.class);
          myContext.startActivity(i);
          return true;
        //Command "about us" -> Open about us page
        case 3:
          i = new Intent(myContext, AboutUsActivity.class);
          myContext.startActivity(i);
          return true;
        //Command "go to surah..." -> Open first page of required Sura
        case 4:
          page = QuranInfo.getPageFromSuraAyah(sura, ayah);
          i = new Intent(myContext, PagerActivity.class);
          i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_SURA, sura);
          i.putExtra(PagerActivity.EXTRA_HIGHLIGHT_AYAH, ayah);
          i.putExtra("page", page);
          myContext.startActivity(i);
          return true;
        //Command "language..." -> changes voice commands language in settings to required language
        case 5:
          return mQuranSettings.setPreferredVoiceLanguage(language);
        default:
          return false;
      }
    }
    //Command cannot be found. show a message to the user to try again and return true
    else {
      CharSequence text = "";
      String notFound = myContext.getText(R.string.command_Not_Found).toString();
      text = notFound + spokenText[0];
      int duration = Toast.LENGTH_LONG;
      Toast toast = Toast.makeText(myContext.getApplicationContext(), text, duration);
      toast.show();
      return true;
    }
  }
  //This function is currently not use. It is added just in case there is a problem converting
  //strings from arabic numbers
  private static final String arabic = "\u06f0\u06f1\u06f2\u06f3\u06f4\u06f5\u06f6\u06f7\u06f8\u06f9";
  private static String arabicToDecimal(String number) {
    char[] chars = new char[number.length()];
    for(int i=0;i<number.length();i++) {
      char ch = number.charAt(i);
      if (ch >= 0x0660 && ch <= 0x0669)
        ch -= 0x0660 - '0';
      else if (ch >= 0x06f0 && ch <= 0x06F9)
        ch -= 0x06f0 - '0';
      chars[i] = ch;
    }
    return new String(chars);
  }
}

