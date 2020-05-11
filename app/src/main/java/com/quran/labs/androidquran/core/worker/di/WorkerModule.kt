package com.quran.labs.androidquran.core.worker.di

import com.quran.labs.androidquran.core.worker.WorkerKey
import com.quran.labs.androidquran.core.worker.WorkerTaskFactory
import com.quran.labs.androidquran.worker.AudioUpdateWorker
import com.quran.labs.androidquran.worker.MissingPageDownloadWorker
import com.quran.labs.androidquran.worker.PartialPageCheckingWorker
import com.quran.labs.androidquran.worker.PartialPageCheckingWorker.Factory
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Module(includes = [ AudioUpdateModule::class ])
abstract class WorkerModule {

  @Binds
  @IntoMap
  @WorkerKey(PartialPageCheckingWorker::class)
  abstract fun bindPartialPageCheckingWorkerFactory(
    workerFactory: Factory
  ): WorkerTaskFactory

  @Binds
  @IntoMap
  @WorkerKey(MissingPageDownloadWorker::class)
  abstract fun bindMissingPageDownloadWorkerFactory(
    workerFactory: MissingPageDownloadWorker.Factory
  ): WorkerTaskFactory

  @Binds
  @IntoMap
  @WorkerKey(AudioUpdateWorker::class)
  abstract fun bindAudioUpdateWorkerFactory(
    workerFactory: AudioUpdateWorker.Factory
  ): WorkerTaskFactory
}
