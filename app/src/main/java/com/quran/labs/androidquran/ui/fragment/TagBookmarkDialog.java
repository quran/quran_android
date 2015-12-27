package com.quran.labs.androidquran.ui.fragment;

import com.quran.labs.androidquran.R;
import com.quran.labs.androidquran.dao.Tag;
import com.quran.labs.androidquran.data.SuraAyah;
import com.quran.labs.androidquran.model.BookmarkModel;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.subscriptions.CompositeSubscription;

public class TagBookmarkDialog extends DialogFragment {

  public static final String TAG = "TagBookmarkDialog";

  private boolean mMadeChanges = false;
  private long mBookmarkId = -1;
  private long[] mBookmarkIds = null;
  private Integer mSura;
  private Integer mAyah;
  private int mPage = -1;
  private List<Tag> mTags;
  private HashSet<Long> mCheckedTags = new HashSet<>();
  private TagsAdapter mAdapter;
  private String mNewTagString;

  private BookmarkModel mBookmarkModel;
  private CompositeSubscription mCompositeSubscription;

  private static final String MADE_CHANGES = "madeChanges";
  private static final String EXTRA_BOOKMARK_ID = "bookmarkid";
  private static final String EXTRA_BOOKMARK_IDS = "bookmarkids";
  private static final String EXTRA_PAGE = "page";
  private static final String EXTRA_SURA = "sura";
  private static final String EXTRA_AYAH = "ayah";
  private static final String TAG_LIST = "taglist";
  private static final String CHECKED_TAG_LIST = "checked_tag_list";

  public static TagBookmarkDialog newInstance(long bookmarkId) {
    final Bundle args = new Bundle();
    args.putLong(EXTRA_BOOKMARK_ID, bookmarkId);
    final TagBookmarkDialog dialog = new TagBookmarkDialog();
    dialog.setArguments(args);
    return dialog;
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
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mCompositeSubscription = new CompositeSubscription();
    mBookmarkModel = BookmarkModel.getInstance(activity);
    mNewTagString = activity.getString(R.string.new_tag);

    Subscription subscription = mBookmarkModel.tagsObservable()
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Tag>() {
          @Override
          public void call(Tag tag) {
            onTagChanged(tag);
          }
        });
    mCompositeSubscription.add(subscription);
  }

  @Override
  public void onDetach() {
    mCompositeSubscription.clear();
    super.onDetach();
  }

  public void updateAyah(SuraAyah suraAyah) {
    updateAyah(suraAyah.sura, suraAyah.ayah, suraAyah.getPage());
  }

  public void updateAyah(int sura, int ayah, int page) {
    mMadeChanges = false;
    mBookmarkId = -1;
    mSura = sura;
    mAyah = ayah;
    mPage = page;
    refresh();
  }

  public void setMadeChanges() {
    mMadeChanges = true;
    // If not in dialog mode, save the changes now, otherwise, on OK
    if (!getShowsDialog()) {
      updateBookmarkTags(false);
    }
  }

  /**
   * Get an Observable with the list of bookmark ids that will be tagged.
   * @return the list of bookmark ids to tag
   */
  private Observable<long[]> getBookmarkIdsObservable() {
    Observable<long[]> observable;
    if (mBookmarkIds != null) {
      // if we already have a list, we just use that
      observable = Observable.just(mBookmarkIds);
    } else if (mBookmarkId > 0) {
      // if we have a bookmark id, use that
      observable = Observable.just(new long[]{ mBookmarkId });
    } else {
      // if we don't have a bookmark id, we'll add the bookmark and use its id
      observable = mBookmarkModel.safeAddBookmark(mSura, mAyah, mPage)
          .map(new Func1<Long, long[]>() {
            @Override
            public long[] call(Long bookmarkId) {
              return new long[]{ bookmarkId };
            }
          });
    }
    return observable;
  }

  private void updateBookmarkTags(final boolean shouldDismiss) {
    Subscription subscription = getBookmarkIdsObservable()
        .flatMap(new Func1<long[], Observable<Boolean>>() {
          @Override
          public Observable<Boolean> call(long[] bookmarkIds) {
            return mBookmarkModel.updateBookmarkTags(
                bookmarkIds, mCheckedTags, mBookmarkIds == null);
          }
        })
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<Boolean>() {
          @Override
          public void call(Boolean aBoolean) {
            mMadeChanges = false;
            final Activity activity = getActivity();
            if (activity instanceof OnBookmarkTagsUpdateListener) {
              ((OnBookmarkTagsUpdateListener) activity).onBookmarkTagsUpdated();
            }

            if (shouldDismiss) {
              dismissAllowingStateLoss();
            }
          }
        });
    mCompositeSubscription.add(subscription);
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    outState.putBoolean(MADE_CHANGES, mMadeChanges);
    outState.putParcelableArrayList(TAG_LIST, (ArrayList<? extends Parcelable>) mTags);
    outState.putSerializable(CHECKED_TAG_LIST, mCheckedTags);
    super.onSaveInstanceState(outState);
  }

  private void onTagChanged(Tag tag) {
    if (mTags != null && mAdapter != null) {
      mCheckedTags.add(tag.id);
      mTags.add(mTags.size() - 1, tag);
      mAdapter.notifyDataSetChanged();
      setMadeChanges();
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    final Bundle args = getArguments();
    if (args != null) {
      mBookmarkId = args.getLong(EXTRA_BOOKMARK_ID);
      mBookmarkIds = args.getLongArray(EXTRA_BOOKMARK_IDS);
      mSura = args.getInt(EXTRA_SURA);
      mAyah = args.getInt(EXTRA_AYAH);
      mPage = args.getInt(EXTRA_PAGE);
    }

    if (mSura != null && mSura == 0) {
      mSura = null;
    }
    if (mAyah != null && mAyah == 0) {
      mAyah = null;
    }

    if (savedInstanceState != null) {
      mMadeChanges = savedInstanceState.getBoolean(MADE_CHANGES);
      mTags = savedInstanceState.getParcelableArrayList(TAG_LIST);
      //noinspection unchecked
      HashSet<Long> set = (HashSet<Long>) savedInstanceState.getSerializable(CHECKED_TAG_LIST);
      if (set != null) {
        mCheckedTags = set;
      }
    }

    if (mTags == null) {
      refresh();
    }
  }

  private void toggleTag(long id) {
    if (mCheckedTags.contains(id)) {
      mCheckedTags.remove(id);
    } else {
      mCheckedTags.add(id);
    }
  }

  private ListView createTagsListView() {
    final FragmentActivity activity = getActivity();

    mAdapter = new TagsAdapter(activity);

    final ListView listview = new ListView(activity);
    listview.setAdapter(mAdapter);
    listview.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
    listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Tag tag = (Tag) mAdapter.getItem(position);
        if (tag.id >= 0) {
          toggleTag(tag.id);
          setMadeChanges();
        } else if (tag.id == -1) {
          Context context = getActivity();
          if (context != null &&
              context instanceof OnBookmarkTagsUpdateListener) {
            ((OnBookmarkTagsUpdateListener) context).onAddTagSelected();
          }
        }

        Object viewTag = view.getTag();
        if (viewTag instanceof ViewHolder) {
          ViewHolder holder = (ViewHolder) viewTag;
          holder.checkBox.setChecked(mCheckedTags.contains(tag.id));
        }
      }
    });
    return listview;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setView(createTagsListView());
    builder.setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        // no-op - set in onStart to avoid closing dialog now
      }
    });
    builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dismiss();
      }
    });
    return builder.create();
  }

  @Override
  public void onStart() {
    super.onStart();
    final Dialog dialog = getDialog();
    if (dialog instanceof AlertDialog) {
      final Button positive = ((AlertDialog) dialog)
          .getButton(Dialog.BUTTON_POSITIVE);
      positive.setOnClickListener(new OnClickListener() {
        @Override
        public void onClick(View v) {
          if (mMadeChanges) {
            updateBookmarkTags(true);
          } else {
            dismiss();
          }
        }
      });
    }
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

  public class TagsAdapter extends BaseAdapter {

    private LayoutInflater mInflater;

    public TagsAdapter(Context context) {
      mInflater = LayoutInflater.from(context);
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
        holder.checkBox = (CheckBox) convertView
            .findViewById(R.id.tag_checkbox);
        holder.tagName = (TextView) convertView
            .findViewById(R.id.tag_name);
        holder.addImage = (ImageView) convertView
            .findViewById(R.id.tag_add_image);
        convertView.setTag(holder);
      }
      final Tag tag = (Tag) getItem(position);
      holder = (ViewHolder) convertView.getTag();
      holder.tagName.setText(tag.toString());
      if (tag.id == -1) {
        holder.addImage.setVisibility(View.VISIBLE);
        holder.checkBox.setVisibility(View.GONE);
      } else {
        holder.addImage.setVisibility(View.GONE);
        holder.checkBox.setVisibility(View.VISIBLE);
        holder.checkBox.setChecked(mCheckedTags.contains(tag.id));
        holder.checkBox.setOnClickListener(new OnClickListener() {
          public void onClick(View v) {
            toggleTag(tag.id);
            setMadeChanges();
          }
        });
      }
      return convertView;
    }
  }

  void refresh() {
    Subscription subscription =
        Observable.zip(mBookmarkModel.getTagsObservable(), getBookmarkTagIdsObservable(),
            new Func2<List<Tag>, List<Long>, RefreshInfo>() {
              @Override
              public RefreshInfo call(List<Tag> tags, List<Long> bookmarkTagIds) {
                return new RefreshInfo(tags, bookmarkTagIds);
              }
            })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(new Action1<RefreshInfo>() {
              @Override
              public void call(RefreshInfo refreshInfo) {
                List<Tag> tags = refreshInfo.tags;
                tags.add(new Tag(-1, mNewTagString));

                List<Long> bookmarkTags = refreshInfo.bookmarkTagIds;
                mCheckedTags.clear();
                for (int i = 0, tagsSize = tags.size(); i < tagsSize; i++) {
                  Tag tag = tags.get(i);
                  if (bookmarkTags.contains(tag.id)) {
                    mCheckedTags.add(tag.id);
                  }
                }
                mMadeChanges = false;
                mTags = tags;
                if (mAdapter != null) {
                  mAdapter.notifyDataSetChanged();
                }
              }
            });
    mCompositeSubscription.add(subscription);
  }

  static class RefreshInfo {
    List<Tag> tags;
    List<Long> bookmarkTagIds;

    public RefreshInfo(List<Tag> tags, List<Long> bookmarkTagIds) {
      this.tags = tags;
      this.bookmarkTagIds = bookmarkTagIds;
    }
  }

  private Observable<List<Long>> getBookmarkTagIdsObservable() {
    Observable<Long> bookmarkId;
    if (mBookmarkIds == null && mBookmarkId < 0 && mPage > 0) {
      bookmarkId = mBookmarkModel.getBookmarkId(mSura, mAyah, mPage);
    } else {
      bookmarkId = Observable.just(mBookmarkIds == null ? mBookmarkId : 0);
    }
    return mBookmarkModel.getBookmarkTagIds(bookmarkId)
        .defaultIfEmpty(new ArrayList<Long>());
  }

  class ViewHolder {
    CheckBox checkBox;
    TextView tagName;
    ImageView addImage;
  }

  public interface OnBookmarkTagsUpdateListener {

    void onBookmarkTagsUpdated();

    void onAddTagSelected();
  }

}
