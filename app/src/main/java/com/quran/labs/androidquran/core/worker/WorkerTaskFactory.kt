package com.quran.labs.androidquran.core.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

interface WorkerTaskFactory {
  fun makeWorker(appContext: Context, workerParameters: WorkerParameters): ListenableWorker
}
