package com.quran.labs.autoquran.di

import com.quran.data.page.provider.QuranDataModule
import com.quran.labs.feature.autoquran.service.QuranBrowsableAudioPlaybackService
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Includes

@DependencyGraph(bindingContainers = [QuranDataModule::class])
interface ServiceGraph {
  fun inject(service: QuranBrowsableAudioPlaybackService)

  @DependencyGraph.Factory
  interface Factory {
    fun create(@Includes serviceBindings: ServiceBindings): ServiceGraph
  }
}
