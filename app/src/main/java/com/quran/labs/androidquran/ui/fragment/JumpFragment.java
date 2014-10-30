package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.ui.PagerActivity;
import com.quran.labs.androidquran.ui.QuranActivity;
import com.quran.labs.androidquran.util.QuranUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;

public class JumpFragment extends DialogFragment {
   public static final String TAG = "JumpFragment";

   public JumpFragment(){
   }

   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState){
      FragmentActivity activity = getActivity();
      LayoutInflater inflater = activity.getLayoutInflater();
      View layout = inflater.inflate(R.layout.jump_dialog, null);

      AlertDialog.Builder builder = new AlertDialog.Builder(activity);
      builder.setTitle(activity.getString(R.string.menu_jump));

      // Sura Spinner
      final Spinner suraSpinner = (Spinner)layout.findViewById(
              R.id.sura_spinner);
      String[] suras = activity.getResources().
          getStringArray(R.array.sura_names);
      for (int i=0; i<suras.length; i++){
        suras[i] = QuranUtils.getLocalizedNumber(activity, (i+1)) +
            ". " + suras[i];
      }
      ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
          activity, android.R.layout.simple_spinner_item, suras);
      adapter.setDropDownViewResource(
              android.R.layout.simple_spinner_dropdown_item);
      suraSpinner.setAdapter(adapter);

      // Ayah Spinner
      final Spinner ayahSpinner = (Spinner)layout.findViewById(
              R.id.ayah_spinner);
      final ArrayAdapter<CharSequence> ayahAdapter =
              new ArrayAdapter<CharSequence>(activity,
                      android.R.layout.simple_spinner_item);
      ayahAdapter.setDropDownViewResource(
              android.R.layout.simple_spinner_dropdown_item);
      ayahSpinner.setAdapter(ayahAdapter);

      // Page text
      final EditText input = (EditText)layout.findViewById(R.id.page_number);

      suraSpinner.setOnItemSelectedListener(
              new AdapterView.OnItemSelectedListener() {
                 @Override
                 public void onItemSelected(AdapterView<?> parent, View view,
                                            int position, long rowId) {
                    int sura = position + 1;
                    int ayahCount = QuranInfo.getNumAyahs(sura);
                    CharSequence[] ayahs = new String[ayahCount];
                    for (int i = 0; i < ayahCount; i++){
                       ayahs[i] = String.valueOf(i + 1);
                    }
                    ayahAdapter.clear();

                     for (int i=0; i<ayahCount; i++){
                        ayahAdapter.add(ayahs[i]);
                     }

                    int page = QuranInfo.getPageFromSuraAyah(sura, 1);
                    input.setHint(String.valueOf(page));
                 }

                 @Override
                 public void onNothingSelected(AdapterView<?> arg0) {
                 }
              });

      ayahSpinner.setOnItemSelectedListener(
              new AdapterView.OnItemSelectedListener() {
                 @Override
                 public void onItemSelected(AdapterView<?> parent, View view,
                                            int position, long rowId) {
                    int ayah = position + 1;
                    int sura = suraSpinner.getSelectedItemPosition() + 1;
                    int page = QuranInfo.getPageFromSuraAyah(sura, ayah);
                    input.setHint(String.valueOf(page));
                 }

                 @Override
                 public void onNothingSelected(AdapterView<?> arg0) {
                 }
              });

      builder.setView(layout);
      builder.setPositiveButton(getString(R.string.dialog_ok),
              new DialogInterface.OnClickListener() {
                 @Override
                 public void onClick(DialogInterface dialog, int which) {
                    try {
                       dialog.dismiss();
                       String text = input.getText().toString();
                       if (TextUtils.isEmpty(text)){
                         text = input.getHint().toString();
                       }
                      
                       int page = Integer.parseInt(text);
                       if (page >= Constants.PAGES_FIRST && page
                               <= Constants.PAGES_LAST) {
                          Activity activity = getActivity();
                          if (activity instanceof QuranActivity) {
                             ((QuranActivity) activity).jumpTo(page);
                          }
                          else if (activity instanceof PagerActivity) {
                             ((PagerActivity) activity).jumpTo(page);
                          }
                       }
                    } catch (Exception e) {
                    }
                 }
              });
      
      return builder.create();
   }
}
