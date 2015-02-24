package com.quran.labs.androidquran.util;

import android.support.annotation.NonNull;

import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// based on PicassoExecutorService
public class QuranExecutorService extends ThreadPoolExecutor {
  private static final int DEFAULT_THREAD_COUNT = 1;

  public QuranExecutorService() {
    super(DEFAULT_THREAD_COUNT, DEFAULT_THREAD_COUNT, 0,
        TimeUnit.MILLISECONDS, new PriorityBlockingQueue<Runnable>());
  }

  @Override
  public Future<?> submit(Runnable task) {
    final QuranFutureTask qft = new QuranFutureTask((QuranPageTask) task);
    execute(qft);
    return qft;
  }

  private static final class QuranFutureTask extends
      FutureTask<QuranPageTask> implements Comparable<QuranFutureTask> {
    private final long mPriority;

    public QuranFutureTask(QuranPageTask task) {
      super(task, null);
      mPriority = System.currentTimeMillis();
    }

    @Override
    public int compareTo(@NonNull QuranFutureTask another) {
      return (int) (another.mPriority - mPriority);
    }
  }
}
