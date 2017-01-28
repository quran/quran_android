package com.quran.labs.androidquran.ui.fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.quran.labs.androidquran.QuranApplication;
import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.presenter.bookmark.TagBookmarkPresenter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

public class TagBookmarkDialog extends DialogFragment {

  public static final String TAG = "TagBookmarkDialog";
  private static final String EXTRA_BOOKMARK_IDS = "bookmark_ids";

  private TagsAdapter mAdapter;
  @Inject TagBookmarkPresenter mTagBookmarkPresenter;


  public static TagBookmarkDialog newInstance(long bookmarkId) {
    return newInstance(new long[] { bookmarkId });
  }

  public static TagBookmarkDialog newInstance(long[] bookmarkIds) {
    final Bundle args = new Bundle();
    args.putLongArray(EXTRA_BOOKMARK_IDS, bookmarkIds);
    final TagBookmarkDialog dialog = new TagBookmarkDialog();
    dialog.setArguments(args);
    return dialog;
  }

  public TagBookmarkDialog() {
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
    ((QuranApplication) context.getApplicationContext()).getApplicationComponent().inject(this);
  }

  public void updateAyah(@NonNull SuraAyah suraAyah) {
    mTagBookmarkPresenter.setAyahBookmarkMode(suraAyah.sura, suraAyah.ayah, suraAyah.getPage());
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final Bundle args = getArguments();
    if (args != null) {
      long[] bookmarkIds = args.getLongArray(EXTRA_BOOKMARK_IDS);

      if (bookmarkIds != null) {
        mTagBookmarkPresenter.setBookmarksMode(bookmarkIds);
      }
    }
  }

  private ListView createTagsListView() {
    final FragmentActivity activity = getActivity();

    mAdapter = new TagsAdapter(activity, mTagBookmarkPresenter);

    final ListView listview = new ListView(activity);
    listview.setAdapter(mAdapter);
    listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listview.setOnItemClickListener((parent, view, position, id) -> {
      Tag tag = (Tag) mAdapter.getItem(position);
      boolean isChecked = mTagBookmarkPresenter.toggleTag(tag.id);

      Object viewTag = view.getTag();
      if (viewTag instanceof ViewHolder) {
        ViewHolder holder = (ViewHolder) viewTag;
        holder.checkBox.setChecked(isChecked);
      }
    });
    return listview;
  }

  public void showAddTagDialog() {
    Context context = getActivity();
    if (context instanceof OnBookmarkTagsUpdateListener) {
      ((OnBookmarkTagsUpdateListener) context).onAddTagSelected();
    }
  }

  public void setData(List<Tag> tags, HashSet<Long> checkedTags) {
    mAdapter.setData(tags, checkedTags);
    mAdapter.notifyDataSetChanged();
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setView(createTagsListView());
    builder.setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
      // no-op - set in onStart to avoid closing dialog now
    });
    builder.setNegativeButton(R.string.cancel, (dialog, which) -> dismiss());
    return builder.create();
  }

  @Override
  public void onStart() {
    super.onStart();
    mTagBookmarkPresenter.bind(this);
    final Dialog dialog = getDialog();
    if (dialog instanceof AlertDialog) {
      final Button positive = ((AlertDialog) dialog)
          .getButton(Dialog.BUTTON_POSITIVE);
      positive.setOnClickListener(v -> {
        mTagBookmarkPresenter.saveChanges();
        dismiss();
      });
    }
  }

  @Override
  public void onStop() {
    mTagBookmarkPresenter.unbind(this);
    super.onStop();
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    // If in dialog mode, don't do anything (or else it will cause exception)
    if (getShowsDialog()) {
      return super.onCreateView(inflater, container, savedInstanceState);
    }
    // If not in dialog mode, treat as normal fragment onCreateView
    return createTagsListView();
  }

  public static class TagsAdapter extends BaseAdapter {

    private LayoutInflater mInflater;
    private TagBookmarkPresenter mTagBookmarkPresenter;

    private String mNewTagString;
    private List<Tag> mTags = new ArrayList<>();
    private HashSet<Long> mCheckedTags = new HashSet<>();

    TagsAdapter(Context context, TagBookmarkPresenter presenter) {
      mInflater = LayoutInflater.from(context);
      mTagBookmarkPresenter = presenter;
      mNewTagString = context.getString(R.string.new_tag);
    }

    void setData(List<Tag> tags, HashSet<Long> checkedTags) {
      mTags = tags;
      mCheckedTags = checkedTags;
    }

    @Override
    public int getCount() {
      return mTags == null ? 0 : mTags.size();
    }

    @Override
    public Object getItem(int position) {
      return mTags.get(position);
    }

    @Override
    public long getItemId(int position) {
      return mTags.get(position).id;
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      ViewHolder holder;
      if (convertView == null) {
        convertView = mInflater.inflate(R.layout.tag_row, parent, false);
        holder = new ViewHolder();
        holder.checkBox = (CheckBox) convertView.findViewById(R.id.tag_checkbox);
        holder.tagName = (TextView) convertView.findViewById(R.id.tag_name);
        holder.addImage = (ImageView) convertView.findViewById(R.id.tag_add_image);
        convertView.setTag(holder);
      }
      final Tag tag = (Tag) getItem(position);
      holder = (ViewHolder) convertView.getTag();
      if (tag.id == -1) {
        holder.addImage.setVisibility(View.VISIBLE);
        holder.checkBox.setVisibility(View.GONE);
        holder.tagName.setText(mNewTagString);
      } else {
        holder.addImage.setVisibility(View.GONE);
        holder.checkBox.setVisibility(View.VISIBLE);
        holder.checkBox.setChecked(mCheckedTags.contains(tag.id));
        holder.tagName.setText(tag.name);
        holder.checkBox.setOnClickListener(v -> mTagBookmarkPresenter.toggleTag(tag.id));
      }
      return convertView;
    }
  }

  static class ViewHolder {
    CheckBox checkBox;
    TextView tagName;
    ImageView addImage;
  }

  public interface OnBookmarkTagsUpdateListener {
    void onAddTagSelected();
  }
}
