package com.quran.labs.androidquran.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import com.actionbarsherlock.app.SherlockDialogFragment;
import com.quran.labs.androidquran.R;

public class AddTagDialog extends SherlockDialogFragment {
   public static final String TAG = "AddTagDialog";

   private long mId = -1;
   private String mName;

   private static final String ID = "id";
   private static final String NAME = "name";

   public AddTagDialog(){
   }

   public AddTagDialog(long id, String name){
      mId = id;
      mName = name;
   }

   @Override
   public void onSaveInstanceState(Bundle outState) {
      outState.putLong(ID, mId);
      outState.putString(NAME, mName);
      super.onSaveInstanceState(outState);
   }

   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState) {
      if (savedInstanceState != null){
         mId = savedInstanceState.getLong(ID, -1);
         mName = savedInstanceState.getString(NAME);
      }

      LayoutInflater inflater = getActivity().getLayoutInflater();
      View layout = inflater.inflate(R.layout.tag_dialog, null);

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle(getString(R.string.tag_dlg_title));

      final EditText nameText =
              (EditText)layout.findViewById(R.id.tag_name);

      if (mId > -1) {
         nameText.setText(mName == null? "" : mName);
      }

      builder.setView(layout);
      builder.setPositiveButton(getString(R.string.dialog_ok),
              new DialogInterface.OnClickListener() {
                 @Override
                 public void onClick(DialogInterface dialog, int which) {
                    Activity activity = getActivity();
                    if (activity != null &&
                        activity instanceof OnTagChangedListener){
                       OnTagChangedListener listener =
                               (OnTagChangedListener)activity;
                       String name = nameText.getText().toString();
                       if (mId > 0){
                          listener.onTagUpdated(mId, name);
                       }
                       else {
                          listener.onTagAdded(name);
                       }
                    }

                    dialog.dismiss();
                 }
              });

      return builder.create();
   }

   public interface OnTagChangedListener {
      public void onTagAdded(String name);
      public void onTagUpdated(long id, String name);
   }
}
