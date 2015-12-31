package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.presenter.bookmark.AddTagDialogPresenter;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

public class AddTagDialog extends DialogFragment {

  public static final String TAG = "AddTagDialog";

  private static final String EXTRA_ID = "id";
  private static final String EXTRA_NAME = "name";

  private AddTagDialogPresenter mAddTagDialogPresenter;

  public static AddTagDialog newInstance(long id, String name) {
    final Bundle args = new Bundle();
    args.putLong(EXTRA_ID, id);
    args.putString(EXTRA_NAME, name);
    final AddTagDialog dialog = new AddTagDialog();
    dialog.setArguments(args);
    return dialog;
  }

  public AddTagDialog() {
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    mAddTagDialogPresenter = new AddTagDialogPresenter(context);
  }

  @Override
  public void onStart() {
    super.onStart();
    mAddTagDialogPresenter.bind(this);
  }

  @Override
  public void onStop() {
    mAddTagDialogPresenter.unbind(this);
    super.onStop();
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    final Bundle args = getArguments();

    final long id;
    final String name;
    if (args != null) {
      id = args.getLong(EXTRA_ID, -1);
      name = args.getString(EXTRA_NAME);
    } else {
      id = -1;
      name = null;
    }

    LayoutInflater inflater = getActivity().getLayoutInflater();
    View layout = inflater.inflate(R.layout.tag_dialog, null);

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(getString(R.string.tag_dlg_title));

    final EditText nameText =
        (EditText) layout.findViewById(R.id.tag_name);

    if (id > -1) {
      nameText.setText(name == null ? "" : name);
    }

    builder.setView(layout);
    builder.setPositiveButton(getString(R.string.dialog_ok),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            String name = nameText.getText().toString();
            if (id > 0) {
              mAddTagDialogPresenter.updateTag(new Tag(id, name));
            } else {
              mAddTagDialogPresenter.addTag(name);
            }

            dismiss();
          }
        });

    return builder.create();
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
  }

  public interface OnTagChangedListener {

    void onTagAdded(String name);

    void onTagUpdated(Tag tag);
  }
}
