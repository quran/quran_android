package com.quran.labs.androidquran.ui.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockDialogFragment;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.data.Constants;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.util.AudioUtils;

import java.util.ArrayList;

public class audioInformationFragment extends SherlockDialogFragment {
    public static final String TAG = "audioInformationFragment";
    public int suraNumber;

    public audioInformationFragment() {
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        FragmentActivity activity = getActivity();
        Context Con = getActivity().getApplicationContext();
        LayoutInflater inflater = activity.getLayoutInflater();
        View layout = inflater.inflate(R.layout.audio_information_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(activity.getString(R.string.menu_aduio_info));


        SharedPreferences mSharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(Con);
        int QariID = mSharedPreferences.getInt(Constants.PREF_DEFAULT_QARI, 0);

        String qariUrl = AudioUtils.getLocalQariUrl(Con, QariID);

        final TextView detailed_file_status_value =
                (TextView) layout.findViewById(R.id.detailed_file_status_value);
        final TextView files_status_value =
                (TextView) layout.findViewById(R.id.files_status_value);
        final TextView reader_name_label_value =
                (TextView) layout.findViewById(R.id.reader_name_label_value);
        String[] quran_readers_name =
                activity.getBaseContext().getResources().getStringArray(R.array.quran_readers_name);

        reader_name_label_value.setText(quran_readers_name[QariID]);

        ArrayList<Integer> AudioInfoArray;
        AudioInfoArray = new ArrayList<Integer>();
        int AudioInfoInt = AudioUtils.getAudioDownloadStatus(
                Con, QariID, qariUrl, suraNumber, AudioInfoArray);

        //1 mean i have the Sura completely
        //2 mean i have partially
        //3 mean I don't have it

        String detailed_file_status_value_text = "";
        if (AudioInfoInt == 1) {

            detailed_file_status_value_text = getString(R.string.AudioStatus_completely);
        }
        if (AudioInfoInt == 2) {

            detailed_file_status_value_text = getString(R.string.AudioStatus_partially);
            String detailed_file_status_value_Audio_output = AudioUtils.getAudioSammary(AudioInfoArray);
            detailed_file_status_value.setText(detailed_file_status_value_Audio_output);

        }
        if (AudioInfoInt == 3) {

            detailed_file_status_value_text = getString(R.string.AudioStatus_missing);
        }

        files_status_value.setText(detailed_file_status_value_text);

        builder.setView(layout);
        builder.setPositiveButton(getString(R.string.dialog_ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            dialog.dismiss();

                        } catch (Exception e) {
                        }
                    }
                });

        return builder.create();
    }
}
