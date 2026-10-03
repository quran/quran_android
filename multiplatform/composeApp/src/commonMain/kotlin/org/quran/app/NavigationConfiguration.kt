package org.quran.app

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

internal val navigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Library::class, Library.serializer())
            subclass(Reader::class, Reader.serializer())
            subclass(Practice::class, Practice.serializer())
            subclass(Study::class, Study.serializer())
            subclass(Qibla::class, Qibla.serializer())
            subclass(Settings::class, Settings.serializer())
        }
    }
}
