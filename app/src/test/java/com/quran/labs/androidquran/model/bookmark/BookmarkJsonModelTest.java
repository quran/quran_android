package com.quran.labs.androidquran.model.bookmark;

import static com.google.common.truth.Truth.assertThat;

import com.quran.data.model.bookmark.BookmarkData;
import com.quran.data.model.bookmark.ReadingBookmarkType;
import com.quran.data.model.bookmark.Tag;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kotlin.time.Instant;
import okio.Buffer;

public class BookmarkJsonModelTest {
  private static final List<Tag> TAGS =
      Arrays.asList(new Tag("tag-1", "First"), new Tag("tag-2", "Second"),
          new Tag("tag-3", "Third"));

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
    assertThat(data.getReadingBookmarks()).isEmpty();
    assertThat(data.getTags()).hasSize(TAGS.size());
    assertThat(data.getTags()).isEqualTo(TAGS);
  }

  @Test
  public void readsRecentPageTimestampsInSecondsAndMilliseconds() throws IOException {
    String json = "{\"recentPages\":["
        + "{\"page\":42,\"timestamp\":1700000000},"
        + "{\"page\":43,\"timestamp\":1700000000123}]}";

    BookmarkData data = jsonModel.fromJson(new Buffer().writeUtf8(json));

    assertThat(data.getRecentPages().get(0).getTimestamp())
        .isEqualTo(Instant.Companion.fromEpochMilliseconds(1_700_000_000_000L));
    assertThat(data.getRecentPages().get(1).getTimestamp())
        .isEqualTo(Instant.Companion.fromEpochMilliseconds(1_700_000_000_123L));
  }

  @Test
  public void exportsInstantTimestampsAsNumericSeconds() throws IOException {
    String json = "{\"recentPages\":[{\"page\":42,\"timestamp\":1700000000123}]}";
    BookmarkData data = jsonModel.fromJson(new Buffer().writeUtf8(json));
    Buffer output = new Buffer();

    jsonModel.toJson(output, data);

    String exportedJson = output.readUtf8();
    assertThat(exportedJson).contains("\"timestamp\":1700000000}");
    BookmarkData restored = jsonModel.fromJson(new Buffer().writeUtf8(exportedJson));
    assertThat(restored.getRecentPages().get(0).getTimestamp().toEpochMilliseconds())
        .isEqualTo(1_700_000_000_000L);
  }

  @Test
  public void roundTripPreservesReadingBookmarkSlotsAndNormalizesTimestampUnits() throws IOException {
    String json = "{\"readingBookmarks\":["
        + "{\"type\":\"page\",\"slot\":\"GREEN\",\"page\":42,\"timestamp\":1700000000},"
        + "{\"type\":\"ayah\",\"slot\":\"BLUE\",\"sura\":2,\"ayah\":255,\"timestamp\":1700000000000},"
        + "{\"type\":\"page\",\"slot\":\"PURPLE\",\"page\":43,\"timestamp\":1700000000}]}";
    BookmarkData data = jsonModel.fromJson(new Buffer().writeUtf8(json));
    Buffer output = new Buffer();

    jsonModel.toJson(output, data);
    BookmarkData restored = jsonModel.fromJson(output);

    assertThat(restored.getReadingBookmarks()).isEqualTo(data.getReadingBookmarks());
    assertThat(restored.getReadingBookmarks().get(0).getSlot()).isEqualTo(ReadingBookmarkType.GREEN);
    assertThat(restored.getReadingBookmarks().get(1).getSlot()).isEqualTo(ReadingBookmarkType.BLUE);
    assertThat(restored.getReadingBookmarks().get(2).getSlot()).isEqualTo(ReadingBookmarkType.PURPLE);
    assertThat(restored.getReadingBookmarks().get(0).getTimestamp().toEpochMilliseconds())
        .isEqualTo(1_700_000_000_000L);
    assertThat(restored.getReadingBookmarks().get(1).getTimestamp().toEpochMilliseconds())
        .isEqualTo(1_700_000_000_000L);
  }
}
