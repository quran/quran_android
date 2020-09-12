package com.quran.labs.androidquran.util;

import android.util.Log;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

import timber.log.Timber;

/**
 * A logging implementation which buffers the last 200 messages.
 * It uploads them to Crashlytics upon the logging of an error.
 *
 * Slightly modified version of Telecine's BugsnagTree.
 * https://github.com/JakeWharton/Telecine
 */
public class RecordingLogTree extends Timber.Tree {

  private static final int BUFFER_SIZE = 200;
  private final FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();

  // Adding one to the initial size accounts for the add before remove.
  private final Deque<String> buffer = new ArrayDeque<>(BUFFER_SIZE + 1);

  @Override
  protected void log(int priority, String tag, @NotNull String message, Throwable t) {
    message = System.currentTimeMillis() + " " + priorityToString(priority) + " " + message;
    synchronized (buffer) {
      buffer.addLast(message);
      if (buffer.size() > BUFFER_SIZE) {
        buffer.removeFirst();
      }
    }

    if (t != null && priority == Log.ERROR) {
      crashlytics.log(getLogs());
      crashlytics.recordException(t);
    }
  }


  public String getLogs() {
    StringBuilder builder = new StringBuilder();
    synchronized (buffer) {
      for (String message : buffer) {
        builder.append(message).append(".\n");
      }
    }
    return builder.toString();
  }

  private static String priorityToString(int priority) {
    switch (priority) {
      case Log.ERROR:
        return "E";
      case Log.WARN:
        return "W";
      case Log.INFO:
        return "I";
      case Log.DEBUG:
        return "D";
      default:
        return String.valueOf(priority);
    }
  }
}
