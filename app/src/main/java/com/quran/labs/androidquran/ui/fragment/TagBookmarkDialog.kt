package com.quran.labs.androidquran.ui.fragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment
import com.quran.data.model.bookmark.Tag
import com.quran.labs.androidquran.QuranApplication
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.presenter.bookmark.TagBookmarkPresenter
import javax.inject.Inject

open class TagBookmarkDialog : DialogFragment() {
  private var adapter: TagsAdapter? = null

  @Inject
  lateinit var tagBookmarkPresenter: TagBookmarkPresenter

  override fun onAttach(context: Context) {
    super.onAttach(context)
    if (shouldInject()) {
      (context.applicationContext as QuranApplication).applicationComponent.inject(this)
    }
  }

  open fun shouldInject() = true

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val args = arguments
    if (args != null) {
      val bookmarkIds = args.getLongArray(EXTRA_BOOKMARK_IDS)
      if (bookmarkIds != null) {
        tagBookmarkPresenter.setBookmarksMode(bookmarkIds)
      }
    }
  }

  private fun createTagsListView(): ListView {
    val context = requireContext()
    adapter = TagsAdapter(context, tagBookmarkPresenter)
    val listview = ListView(context)
    listview.adapter = adapter
    listview.choiceMode = ListView.CHOICE_MODE_MULTIPLE
    listview.onItemClickListener =
      OnItemClickListener { _: AdapterView<*>?, view: View, position: Int, _: Long ->
        val tag = adapter!!.getItem(position)
        val isChecked = tagBookmarkPresenter.toggleTag(tag.id)
        val viewTag = view.tag
        if (viewTag is ViewHolder) {
          viewTag.checkBox.isChecked = isChecked
        }
      }
    return listview
  }

  fun showAddTagDialog() {
    val context: Context? = activity
    if (context is OnBookmarkTagsUpdateListener) {
      (context as OnBookmarkTagsUpdateListener).onAddTagSelected()
    }
  }

  fun setData(tags: List<Tag>?, checkedTags: HashSet<Long>) {
    adapter!!.setData(tags, checkedTags)
    adapter!!.notifyDataSetChanged()
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val builder = Builder(requireActivity())
    builder.setView(createTagsListView())
    builder.setPositiveButton(R.string.dialog_ok) { _: DialogInterface?, _: Int -> }
    builder.setNegativeButton(com.quran.mobile.common.ui.core.R.string.cancel) { _: DialogInterface?, _: Int -> dismiss() }
    return builder.create()
  }

  override fun onStart() {
    super.onStart()
    tagBookmarkPresenter.bind(this)

    val dialog = dialog
    if (dialog is AlertDialog) {
      val positive = dialog.getButton(Dialog.BUTTON_POSITIVE)
      positive.setOnClickListener {
        tagBookmarkPresenter.saveChanges()
        dismiss()
      }
    }
  }

  override fun onStop() {
    tagBookmarkPresenter.unbind(this)
    super.onStop()
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    // If in dialog mode, don't do anything (or else it will cause exception)
    return if (showsDialog) {
      super.onCreateView(inflater, container, savedInstanceState)
    } else createTagsListView()
    // If not in dialog mode, treat as normal fragment onCreateView
  }

  class TagsAdapter internal constructor(
    context: Context, presenter: TagBookmarkPresenter
  ) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val tagBookmarkPresenter: TagBookmarkPresenter = presenter
    private val newTagString: String = context.getString(R.string.new_tag)
    private var tags: List<Tag> = emptyList()
    private var checkedTags = HashSet<Long>()

    fun setData(tags: List<Tag>?, checkedTags: HashSet<Long>) {
      this.tags = (tags ?: emptyList())
      this.checkedTags = checkedTags
    }

    override fun getCount(): Int = tags.size

    override fun getItem(position: Int): Tag = tags[position]

    override fun getItemId(position: Int): Long = tags[position].id

    override fun hasStableIds(): Boolean = false

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      var view = convertView

      var holder: ViewHolder
      if (view == null) {
        view = inflater.inflate(R.layout.tag_row, parent, false)
        holder = ViewHolder().apply {
          checkBox = view.findViewById(R.id.tag_checkbox)
          tagName = view.findViewById(R.id.tag_name)
          addImage = view.findViewById(R.id.tag_add_image)
        }
        view.tag = holder
      }

      val (id, name) = getItem(position)
      holder = view!!.tag as ViewHolder
      if (id == -1L) {
        holder.apply {
          addImage.visibility = View.VISIBLE
          checkBox.visibility = View.GONE
          tagName.text = newTagString
        }
      } else {
        holder.apply {
          addImage.visibility = View.GONE
          checkBox.visibility = View.VISIBLE
          checkBox.isChecked = checkedTags.contains(id)
          tagName.text = name
          checkBox.setOnClickListener { tagBookmarkPresenter.toggleTag(id) }
        }
      }
      return view
    }
  }

  internal class ViewHolder {
    lateinit var checkBox: CheckBox
    lateinit var tagName: TextView
    lateinit var addImage: ImageView
  }

  interface OnBookmarkTagsUpdateListener {
    fun onAddTagSelected()
  }

  companion object {
    const val TAG = "TagBookmarkDialog"
    private const val EXTRA_BOOKMARK_IDS = "bookmark_ids"
    fun newInstance(bookmarkId: Long): TagBookmarkDialog {
      return newInstance(longArrayOf(bookmarkId))
    }

    fun newInstance(bookmarkIds: LongArray?): TagBookmarkDialog {
      val args = Bundle()
      args.putLongArray(EXTRA_BOOKMARK_IDS, bookmarkIds)
      val dialog = TagBookmarkDialog()
      dialog.arguments = args
      return dialog
    }
  }
}
