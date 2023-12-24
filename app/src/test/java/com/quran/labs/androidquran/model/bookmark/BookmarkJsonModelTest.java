package com.quran.labs.androidquran.model.bookmark;

import com.quran.data.model.bookmark.BookmarkData;
import com.quran.data.model.bookmark.Tag;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okio.Buffer;

import static com.google.common.truth.Truth.assertThat;

public class BookmarkJsonModelTest {
  private static final List<Tag> TAGS =
      Arrays.asList(new Tag(1, "First"), new Tag(2, "Second"), new Tag(3, "Third"));

  private BookmarkJsonModel jsonModel;

  @Before
  public void setUp() {
    jsonModel = new BookmarkJsonModel();
  }

  @Test
  public void simpleTestToFromJson() throws IOException {
    BookmarkData inputData = new BookmarkData(TAGS, new ArrayList<>(), new ArrayList<>());
    Buffer output = new Buffer();
    jsonModel.toJson(output, inputData);
    String result = output.readUtf8();

    Buffer buffer = new Buffer().writeUtf8(result);
    BookmarkData data = jsonModel.fromJson(buffer);
    assertThat(data).isNotNull();
    assertThat(data.getBookmarks()).isEmpty();
    assertThat(data.getTags()).hasSize(TAGS.size());
    assertThat(data.getTags()).isEqualTo(TAGS);
  }
}
