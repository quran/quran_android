package com.quran.labs.androidquran.data;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.quran.labs.androidquran.database.DatabaseUtils;
import com.quran.labs.androidquran.util.QuranFileUtils;
import com.quran.page.common.data.AyahBounds;
import com.quran.page.common.data.AyahCoordinates;
import com.quran.page.common.data.AyahMarkerLocation;
import com.quran.page.common.data.PageCoordinates;
import com.quran.page.common.data.SuraHeaderLocation;
import com.quran.page.common.data.coordinates.PageGlyphsCoords;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AyahInfoDatabaseHandler {
  private static final RectF EMPTY_BOUNDS = new RectF();
  private static final String COL_PAGE = "page_number";
  private static final String COL_LINE = "line_number";
  private static final String COL_SURA = "sura_number";
  private static final String COL_AYAH = "ayah_number";
  private static final String COL_POSITION = "position";
  private static final String MIN_X = "min_x";
  private static final String MIN_Y = "min_y";
  private static final String MAX_X = "max_x";
  private static final String MAX_Y = "max_y";
  private static final String GLYPH_TYPE = "glyph_type";
  private static final String GLYPHS_TABLE = "glyphs";

  private static final Map<String, AyahInfoDatabaseHandler> ayahInfoCache = new HashMap<>();
  private static File quranDatabaseDirectory = null;

  private final SQLiteDatabase database;
  private final boolean hasGlyphData;
  private final int xCoordinateOffset;

  static AyahInfoDatabaseHandler getAyahInfoDatabaseHandler(
      String databaseName, QuranFileUtils quranFileUtils) {
    final File currentAyahDatabaseDirectory = quranFileUtils.getQuranAyahDatabaseDirectory();
    if (!currentAyahDatabaseDirectory.equals(quranDatabaseDirectory)) {
      quranDatabaseDirectory = currentAyahDatabaseDirectory;
      clearAyahInfoCache();
    }

    AyahInfoDatabaseHandler handler = ayahInfoCache.get(databaseName);
    if (handler == null) {
      try {
        AyahInfoDatabaseHandler db = new AyahInfoDatabaseHandler(databaseName, quranFileUtils);
        if (db.validDatabase()) {
          ayahInfoCache.put(databaseName, db);
          handler = db;
        }
      } catch (SQLException sqlException) {
        // it's okay, we'll try again later
      }
    }
    return handler;
  }

  private static void clearAyahInfoCache() {
    final Set<String> keys = ayahInfoCache.keySet();
    for (String key : keys) {
      final AyahInfoDatabaseHandler handler = ayahInfoCache.get(key);
      try {
        if (handler != null) {
          handler.database.close();
        }
      } catch (Exception e) {
        // ignore
      }
    }
    ayahInfoCache.clear();
  }

  private AyahInfoDatabaseHandler(String databaseName,
                                  QuranFileUtils quranFileUtils) throws SQLException {
    File base = quranFileUtils.getQuranAyahDatabaseDirectory();
    File path = new File(base, databaseName);
    database = SQLiteDatabase.openDatabase(path.getAbsolutePath(), null, SQLiteDatabase.NO_LOCALIZED_COLLATORS);
    hasGlyphData = quranFileUtils.getAyahInfoDbHasGlyphData();
    // Tajweed width_1280 PNG images are 1340x1890 (30-31px left/right border margin around the 1280px coordinate space)
    boolean isTajweedDb = "ayahinfo_1280.db".equals(databaseName) ||
        base.getAbsolutePath().contains("tajweed");
    xCoordinateOffset = isTajweedDb ? 31 : 0;
  }

  private boolean validDatabase() {
    return database != null && database.isOpen();
  }

  @NonNull
  public PageCoordinates getPageInfo(int page, boolean wantBounds) {
    final RectF bounds = wantBounds ? getPageBounds(page) : EMPTY_BOUNDS;
    if (haveVerseMarkerData()) {
      return new PageCoordinates(page,
          bounds,
          getSuraHeadersForPage(page),
          getVerseMarkersForPage(page));
    } else {
      return new PageCoordinates(page, bounds, new ArrayList<>(), new ArrayList<>());
    }
  }

  @NonNull
  private RectF getPageBounds(int page) {
    Cursor c = null;
    try {
      String[] colNames = new String[]{
          "MIN(" + MIN_X + ")", "MIN(" + MIN_Y + ")",
          "MAX(" + MAX_X + ")", "MAX(" + MAX_Y + ")" };
      c = database.query(GLYPHS_TABLE, colNames, COL_PAGE + "=" + page, null, null, null, null);
      if (c.moveToFirst()) {
        return new RectF(
            c.getInt(0) + xCoordinateOffset,
            c.getInt(1),
            c.getInt(2) + xCoordinateOffset,
            c.getInt(3)
        );
      } else {
        throw new IllegalArgumentException("getPageBounds() on a non-existent page: " + page);
      }
    } finally {
      DatabaseUtils.closeCursor(c);
    }
  }

  private static class RawGlyphRow {
    final int line;
    final int sura;
    final int ayah;
    final int position;
    final int rawWidth;
    final RectF bounds;
    final String glyphType;

    RawGlyphRow(int line, int sura, int ayah, int position, int rawWidth, RectF bounds, String glyphType) {
      this.line = line;
      this.sura = sura;
      this.ayah = ayah;
      this.position = position;
      this.rawWidth = rawWidth;
      this.bounds = bounds;
      this.glyphType = glyphType;
    }
  }

  @NonNull
  public AyahCoordinates getVersesBoundsForPage(int page) {
    boolean hasGlyphTypeColumn = haveGlyphData();
    GlyphsBuilder glyphsBuilder = new GlyphsBuilder();
    Map<String, List<AyahBounds>> ayahBounds = new HashMap<>();
    List<RawGlyphRow> rawRows = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = getVersesBoundsCursorForPage(page, hasGlyphTypeColumn);
      while (cursor.moveToNext()) {
        int line = cursor.getInt(1);
        int sura = cursor.getInt(2);
        int ayah = cursor.getInt(3);
        int position = cursor.getInt(4);
        int rawMinX = cursor.getInt(5) + xCoordinateOffset;
        int rawMinY = cursor.getInt(6);
        int rawMaxX = cursor.getInt(7) + xCoordinateOffset;
        int rawMaxY = cursor.getInt(8);
        String glyphType = hasGlyphTypeColumn ? cursor.getString(9) : null;

        int rawWidth = rawMaxX - rawMinX;
        int minX = Math.min(rawMinX, rawMaxX);
        int maxX = Math.max(rawMinX, rawMaxX);
        int minY = Math.min(rawMinY, rawMaxY);
        int maxY = Math.max(rawMinY, rawMaxY);

        RectF r = new RectF(minX, minY, maxX, maxY);
        rawRows.add(new RawGlyphRow(line, sura, ayah, position, rawWidth, r, glyphType));

        String key = sura + ":" + ayah;
        List<AyahBounds> bounds = ayahBounds.get(key);
        if (bounds == null) {
          bounds = new ArrayList<>();
        }

        AyahBounds last = null;
        if (bounds.size() > 0) {
          last = bounds.get(bounds.size() - 1);
        }

        AyahBounds bound = new AyahBounds(line, position, minX, minY, maxX, maxY);
        if (last != null && last.getLine() == bound.getLine()) {
          last.engulf(bound);
        } else {
          bounds.add(bound);
        }
        ayahBounds.put(key, bounds);
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }

    int i = 0;
    int size = rawRows.size();
    while (i < size) {
      int startIdx = i;
      RawGlyphRow first = rawRows.get(startIdx);
      int endIdx = startIdx;
      while (endIdx + 1 < size &&
          rawRows.get(endIdx + 1).sura == first.sura &&
          rawRows.get(endIdx + 1).ayah == first.ayah) {
        endIdx++;
      }

      int ayahRowCount = endIdx - startIdx + 1;
      int nonPauseNonEndCount = 0;
      for (int k = startIdx; k <= endIdx; k++) {
        boolean isEnd = (k == endIdx && ayahRowCount > 1);
        if (!isEnd && rawRows.get(k).rawWidth > 18) {
          nonPauseNonEndCount++;
        }
      }
      int expectedWords = com.quran.labs.androidquran.memorization.QuranMemorizationManager.INSTANCE
          .getWordCountForAyah(new com.quran.data.model.SuraAyah(first.sura, first.ayah));
      boolean firstIsHizb = (expectedWords > 0 &&
          nonPauseNonEndCount == expectedWords + 1 &&
          first.position == 1 &&
          first.rawWidth > 18);

      for (int k = startIdx; k <= endIdx; k++) {
        RawGlyphRow row = rawRows.get(k);
        String type = row.glyphType;
        RectF bounds = row.bounds;
        if (type == null || type.isEmpty()) {
          if (k == endIdx && ayahRowCount > 1) {
            type = "end";
          } else if (row.rawWidth <= 18) {
            type = "pause";
            float cx = (bounds.left + bounds.right) / 2f;
            bounds = new RectF(cx - 14f, bounds.top - 2f, cx + 14f, bounds.bottom + 2f);
          } else if (k == startIdx && firstIsHizb) {
            type = "hizb";
          } else {
            type = "word";
          }
        }
        glyphsBuilder.append(row.sura, row.ayah, row.position, row.line, type, bounds);
      }

      i = endIdx + 1;
    }

    List<com.quran.page.common.data.coordinates.GlyphCoords> builtGlyphs = glyphsBuilder.build();
    PageGlyphsCoords glyphCoords = builtGlyphs.isEmpty() ? null :
        new PageGlyphsCoords(page, builtGlyphs);

    return new AyahCoordinates(page, ayahBounds, glyphCoords);
  }

  private boolean haveVerseMarkerData() {
    Cursor cursor = null;
    try {
      cursor = database.rawQuery("SELECT count(1) from sqlite_master WHERE name = ?",
          new String[] { "ayah_markers" });
      return cursor.moveToFirst() && cursor.getInt(0) > 0;
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
  }

  private boolean haveGlyphData() {
    Cursor cursor = null;
    try {
      cursor = database.query(GLYPHS_TABLE,
          new String[]{GLYPH_TYPE}, null, null, null, null, null, "1");
      return cursor.moveToFirst() && cursor.getString(0) != null;
    } catch (Exception e) {
      // we don't have glyph data (the column doesn't exist)
      return false;
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
  }

  private List<AyahMarkerLocation> getVerseMarkersForPage(int page) {
    final List<AyahMarkerLocation> markers = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = database.query("ayah_markers",
          new String[] { "sura_number", "ayah_number", "x", "y" },
          "page_number = ?", new String[] { String.valueOf(page) }, null,
          null, "sura_number, ayah_number ASC");
      while (cursor.moveToNext()) {
        final int sura = cursor.getInt(0);
        final int ayah = cursor.getInt(1);
        final int x = cursor.getInt(2);
        final int y = cursor.getInt(3);
        markers.add(new AyahMarkerLocation(sura, ayah, x, y));
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return markers;
  }

  private List<SuraHeaderLocation> getSuraHeadersForPage(int page) {
    final List<SuraHeaderLocation> headers = new ArrayList<>();
    Cursor cursor = null;
    try {
      cursor = database.query("sura_headers",
          new String[] { "sura_number", "x", "y", "width", "height" },
          "page_number = ?", new String[] { String.valueOf(page) }, null,
          null, "sura_number ASC");
      while (cursor.moveToNext()) {
        final int sura = cursor.getInt(0);
        final int x = cursor.getInt(1);
        final int y = cursor.getInt(2);
        final int width = cursor.getInt(3);
        final int height = cursor.getInt(4);
        headers.add(new SuraHeaderLocation(sura, x, y, width, height));
      }
    } finally {
      DatabaseUtils.closeCursor(cursor);
    }
    return headers;
  }

  private Cursor getVersesBoundsCursorForPage(int page, boolean withGlyphData) {
    String[] columns = withGlyphData ?
        new String[]{COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y, GLYPH_TYPE} :
        new String[]{COL_PAGE, COL_LINE, COL_SURA, COL_AYAH, COL_POSITION, MIN_X, MIN_Y, MAX_X, MAX_Y};

    return database.query(GLYPHS_TABLE,
        columns,
        COL_PAGE + "=" + page,
        null, null, null,
        COL_SURA + "," + COL_AYAH + "," + COL_POSITION);
  }
}
