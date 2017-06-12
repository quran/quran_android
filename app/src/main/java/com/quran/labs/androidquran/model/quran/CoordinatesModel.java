package com.quran.labs.androidquran.model.quran;

import android.graphics.RectF;
import android.support.v4.util.Pair;

import com.quran.labs.androidquran.common.AyahBounds;
import com.quran.labs.androidquran.data.AyahInfoDatabaseHandler;
import com.quran.labs.androidquran.data.AyahInfoDatabaseProvider;
import com.quran.labs.androidquran.di.ActivityScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.inject.Inject;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

@ActivityScope
public class CoordinatesModel {
  private static final int PIXEL_THRESHOLD = 10;

  private final AyahInfoDatabaseProvider ayahInfoDatabaseProvider;

  @Inject
  CoordinatesModel(AyahInfoDatabaseProvider ayahInfoDatabaseProvider) {
    this.ayahInfoDatabaseProvider = ayahInfoDatabaseProvider;
  }

  public Observable<Pair<Integer, RectF>> getPageCoordinates(Integer... pages) {
    AyahInfoDatabaseHandler database = ayahInfoDatabaseProvider.getAyahInfoHandler();
    if (database == null) {
      return Observable.error(new NoSuchElementException("No AyahInfoDatabaseHandler found!"));
    }

    return Observable.fromArray(pages)
        .map(page -> new Pair<>(page, database.getPageBounds(page)))
        .subscribeOn(Schedulers.computation());
  }

  public Observable<Pair<Integer, Map<String, List<AyahBounds>>>> getAyahCoordinates(
      Integer... pages) {
    AyahInfoDatabaseHandler database = ayahInfoDatabaseProvider.getAyahInfoHandler();
    if (database == null) {
      return Observable.error(new NoSuchElementException("No AyahInfoDatabaseHandler found!"));
    }

    return Observable.fromArray(pages)
        .map(page -> new Pair<>(page, database.getVersesBoundsForPage(page)))
        .map(pair -> new Pair<>(pair.first, normalizeMap(pair.second)))
        .subscribeOn(Schedulers.computation());
  }

  private Map<String, List<AyahBounds>> normalizeMap(Map<String, List<AyahBounds>> original) {
    Map<String, List<AyahBounds>> normalizedMap = new HashMap<>();
    final Set<String> keys = original.keySet();
    for (String key : keys) {
      List<AyahBounds> normalBounds = original.get(key);
      normalizedMap.put(key, normalizeAyahBounds(normalBounds));
    }
    return normalizedMap;
  }

  private List<AyahBounds> normalizeAyahBounds(List<AyahBounds> ayahBounds) {
    int total = ayahBounds.size();
    if (total < 2) {
      // only one line, nothing to normalize
      return ayahBounds;
    }

    AyahBounds middle = null;
    for (int i = 1; i < (total - 1); i++) {
      if (middle == null) {
        middle = ayahBounds.get(i);
      } else {
        middle.engulf(ayahBounds.get(i));
      }
    }

    AyahBounds first = ayahBounds.get(0);
    AyahBounds last = ayahBounds.get(total - 1);

    RectF firstRect = first.getBounds();
    RectF lastRect = last.getBounds();
    if (middle != null) {
      // the middle bounds must, by definition, be full lines, and the first line must, by
      // definition, reach the end of the line. normalize these to be the least x value.
      RectF middleRect = middle.getBounds();
      float bestEndOfLine = Math.min(firstRect.left, middleRect.left);

      // also take into account the last line if it it goes to the very end (otherwise, it must
      // be greater than bestEndOfLine, so this will have no effect)
      bestEndOfLine = Math.min(bestEndOfLine, lastRect.left);
      firstRect.left = bestEndOfLine;
      middleRect.left = bestEndOfLine;

      // update the left of the last row only if it's a full row
      if (Math.abs(lastRect.left - bestEndOfLine) < PIXEL_THRESHOLD) {
        lastRect.left = bestEndOfLine;
      }

      // let the top line touch the middle line
      firstRect.bottom = middleRect.top;

      // the last line must, by definition, start at the right hand side of the screen, so
      // normalize that with the right side of the middle such that they are the max x value.
      float bestStartOfLine = Math.max(lastRect.right, middleRect.right);

      // also take into account the first line if it fills the entire line. if it doesn't, then
      // the right must be less than bestStartOfLine, so this will have no effect.
      bestStartOfLine = Math.max(bestStartOfLine, firstRect.right);
      lastRect.right = bestStartOfLine;
      middleRect.right = bestStartOfLine;

      // update the right of the first row only if it's a full row
      if (Math.abs(firstRect.right - bestStartOfLine) < PIXEL_THRESHOLD) {
        firstRect.right = bestStartOfLine;
      }

      // let the bottom line touch the middle line
      lastRect.top = middleRect.bottom;

      // get the updated ayah bounds
      first = first.withBounds(firstRect);
      middle = middle.withBounds(middleRect);
      last = last.withBounds(lastRect);
    } else {
      // only 2 lines - let's see if any of them are full lines
      boolean firstIsFullLine = Math.abs(firstRect.right - lastRect.right) < PIXEL_THRESHOLD;
      boolean secondIsFullLine = Math.abs(firstRect.left - lastRect.left) < PIXEL_THRESHOLD;
      if (firstIsFullLine && secondIsFullLine) {
        first.engulf(last);
        return Collections.singletonList(first);
      } else if (firstIsFullLine) {
        lastRect.top = firstRect.bottom;
        float bestStartOfLine = Math.max(firstRect.right, lastRect.right);
        firstRect.right = bestStartOfLine;
        lastRect.right = bestStartOfLine;

        first = first.withBounds(firstRect);
        last = last.withBounds(lastRect);
      } else if (secondIsFullLine) {
        firstRect.bottom = lastRect.top;
        float bestEndOfLine = Math.min(firstRect.left, lastRect.left);
        firstRect.left = bestEndOfLine;
        lastRect.left = bestEndOfLine;

        first = first.withBounds(firstRect);
        last = last.withBounds(lastRect);
      } else {
        // neither one is a full line, let's generate a middle entry to join them if they have
        // anything in common (i.e. any part of them intersects)
        if (lastRect.left < firstRect.right) {
          RectF middleBounds = new RectF(lastRect.left,
              firstRect.bottom,
              firstRect.right,
              lastRect.top);
          middle = new AyahBounds(first.getLine(), first.getPosition(), middleBounds);
        }
      }
    }

    List<AyahBounds> result = new ArrayList<>();
    result.add(first);
    if (middle != null) {
      result.add(middle);
    }
    result.add(last);
    return result;
  }
}
