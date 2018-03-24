package com.quran.labs.androidquran.component.application;

import com.quran.data.page.provider.QuranPageModule;
import com.quran.labs.androidquran.data.QuranDataModule;
import com.quran.labs.androidquran.module.application.ApplicationModule;
import com.quran.labs.androidquran.module.application.DatabaseModule;
import com.quran.labs.androidquran.module.application.DebugNetworkModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = {
    ApplicationModule.class,
    DatabaseModule.class,
    DebugNetworkModule.class,
    QuranDataModule.class,
    QuranPageModule.class } )
interface DebugApplicationComponent extends ApplicationComponent {
}
