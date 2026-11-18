package com.quran.labs.androidquran.buildutil.qaricatalog

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class QariCatalogExtension {
  abstract val catalogFile: RegularFileProperty
  abstract val overrideProperty: Property<String>
}
