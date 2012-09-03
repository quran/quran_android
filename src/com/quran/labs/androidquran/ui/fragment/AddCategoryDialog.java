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

public class AddCategoryDialog extends SherlockDialogFragment {

   private long mId = -1;
   private String mName;
   private String mDescription;

   public AddCategoryDialog(){
   }

   public AddCategoryDialog(long id, String name, String description){
      mId = id;
      mName = name;
      mDescription = description;
   }

   @Override
   public Dialog onCreateDialog(Bundle savedInstanceState) {
      LayoutInflater inflater = getActivity().getLayoutInflater();
      View layout = inflater.inflate(R.layout.bookmark_dialog, null);

      AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
      builder.setTitle(getString(R.string.bookmark_category_title));

      final EditText nameText =
              (EditText)layout.findViewById(R.id.bookmark_name);
      final EditText descriptionText =
              (EditText)layout.findViewById(R.id.bookmark_description);

      if (mId > -1) {
         nameText.setText(mName == null? "" : mName);
         descriptionText.setText(mDescription == null ? "" : mDescription);
      }

      builder.setView(layout);
      builder.setPositiveButton(getString(R.string.dialog_ok),
              new DialogInterface.OnClickListener() {
                 @Override
                 public void onClick(DialogInterface dialog, int which) {
                    Activity activity = getActivity();
                    if (activity != null &&
                        activity instanceof OnCategoryChangedListener){
                       OnCategoryChangedListener listener =
                               (OnCategoryChangedListener)activity;
                       String name = nameText.getText().toString();
                       String description =
                               descriptionText.getText().toString();
                       if (mId > 0){
                          listener.onCategoryUpdated(mId, name, description);
                       }
                       else {
                          listener.onCategoryAdded(name,  description);
                       }
                    }

                    dialog.dismiss();
                 }
              });

      return builder.create();
   }

   public interface OnCategoryChangedListener {
      public void onCategoryAdded(String name, String description);
      public void onCategoryUpdated(long id, String name, String description);
   }
}
