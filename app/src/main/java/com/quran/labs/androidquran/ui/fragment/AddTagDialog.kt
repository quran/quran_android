package com.quran.labs.androidquran.ui.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.presenter.bookmark.AddTagDialogPresenter
import javax.inject.Inject

class AddTagDialog : DialogFragment() {

  @Inject
  internal lateinit var addTagDialogPresenter: AddTagDialogPresenter
  private var textInputEditText: TextInputEditText? = null

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (context.applicationContext as QuranApplication).applicationComponent.inject(this)
  }

  override fun onStart() {
    super.onStart()
    addTagDialogPresenter.bind(this)
  }

  override fun onStop() {
    addTagDialogPresenter.unbind(this)
    super.onStop()
  }

  override fun onResume() {
    super.onResume()
    (dialog as AlertDialog).let {
      it.getButton(Dialog.BUTTON_POSITIVE)
          .setOnClickListener {
            val id = arguments?.getLong(EXTRA_ID, -1) ?: -1
            val name = textInputEditText?.text.toString()
            val success = addTagDialogPresenter.validate(name, id)
            if (success) {
              if (id > -1) {
                addTagDialogPresenter.updateTag(Tag(id, name))
              } else {
                addTagDialogPresenter.addTag(name)
              }
              dismiss()
            }
          }
    }

    textInputEditText?.requestFocus()
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val args = arguments
    val id = args?.getLong(EXTRA_ID, -1) ?: -1
    val originalName = args?.getString(EXTRA_NAME, "") ?: ""

    val activity = requireActivity()
    val inflater = activity.layoutInflater

    @SuppressLint("InflateParams")
    val layout = inflater.inflate(R.layout.tag_dialog, null)

    val builder = AlertDialog.Builder(activity)
    builder.setTitle(getString(R.string.tag_dlg_title))

    val text = layout.findViewById<TextInputEditText>(R.id.tag_name)
    if (id > -1) {
      text.setText(originalName)
      text.setSelection(originalName.length)
    }

    textInputEditText = text

    builder.setView(layout)
    builder.setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> }

    return builder.create()
  }

  fun onBlankTagName() {
    textInputEditText?.error = activity?.getString(R.string.tag_blank_tag_error)
  }

  fun onDuplicateTagName() {
    textInputEditText?.error = activity?.getString(R.string.tag_duplicate_tag_error)
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    dialog?.window!!.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
  }

  companion object {
    const val TAG = "AddTagDialog"

    private const val EXTRA_ID = "id"
    private const val EXTRA_NAME = "name"

    fun newInstance(id: Long, name: String): AddTagDialog {
      val args = Bundle()
      args.putLong(EXTRA_ID, id)
      args.putString(EXTRA_NAME, name)
      val dialog = AddTagDialog()
      dialog.arguments = args
      return dialog
    }
  }

}
