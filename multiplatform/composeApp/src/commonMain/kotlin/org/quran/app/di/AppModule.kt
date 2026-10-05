package org.quran.app.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.quran.app.data.BundledQuranRepository
import org.quran.app.data.StoredProgressRepository
import org.quran.app.domain.SettingsStore

fun appModule(settings: SettingsStore): Module = module {
    single<SettingsStore> { settings }
    single { BundledQuranRepository() }
    single { StoredProgressRepository(get()) }
}
