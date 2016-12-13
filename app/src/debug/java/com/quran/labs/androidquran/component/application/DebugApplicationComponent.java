package com.quran.labs.androidquran.component.application;

import com.quran.labs.androidquran.module.application.ApplicationModule;
import com.quran.labs.androidquran.module.application.DatabaseModule;
import com.quran.labs.androidquran.module.application.DebugNetworkModule;

import javax.inject.Singleton;

import dagger.Component;

@Singleton
@Component(modules = { ApplicationModule.class, DatabaseModule.class, DebugNetworkModule.class } )
interface DebugApplicationComponent extends ApplicationComponent {
}
