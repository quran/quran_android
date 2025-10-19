package com.quran.labs.androidquran.core.worker

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass

class QuranWorkerFactory @Inject constructor(
  private val workerTaskFactories: Map<KClass<out ListenableWorker>, Provider<WorkerTaskFactory>>
): WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters
  ): ListenableWorker? {
    val workerClass = Class.forName(workerClassName)
    val factory = workerTaskFactories.entries.find { workerClass.kotlin == it.key }?.value
    return factory?.get()?.makeWorker(appContext, workerParameters)
  }
}
