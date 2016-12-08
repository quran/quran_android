package com.quran.labs.androidquran.model.bookmark;

import com.quran.labs.androidquran.dao.BookmarkData;
import com.quran.labs.androidquran.dao.Tag;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okio.Buffer;

import static com.google.common.truth.Truth.assertThat;

public class BookmarkJsonModelTest {
  private static final String TAGS_JSON =
      "{\"bookmarks\":[],\"recentPages\":[],\"tags\":[{\"id\":1,\"name\":\"First\"}," +
      "{\"id\":2,\"name\":\"Second\"},{\"id\":3,\"name\":\"Third\"}]}";
  private static final List<Tag> TAGS =
      Arrays.asList(new Tag(1, "First"), new Tag(2, "Second"), new Tag(3, "Third"));

  private BookmarkJsonModel jsonModel;

  @Before
  public void setUp() {
    jsonModel = new BookmarkJsonModel();
  }

  @Test
  public void simpleTestToJson() throws IOException {
    BookmarkData data = new BookmarkData(TAGS, new ArrayList<>(), new ArrayList<>());
    Buffer output = new Buffer();
    jsonModel.toJson(output, data);
    String result = output.readUtf8();
    assertThat(result).isEqualTo(TAGS_JSON);
  }

  @Test
  public void simpleTestFromJson() throws IOException {
    Buffer buffer = new Buffer().writeUtf8(TAGS_JSON);
    BookmarkData data = jsonModel.fromJson(buffer);
    assertThat(data).isNotNull();
    assertThat(data.getBookmarks()).isEmpty();
    assertThat(data.getTags()).hasSize(TAGS.size());
    assertThat(data.getTags()).isEqualTo(TAGS);
  }
}
