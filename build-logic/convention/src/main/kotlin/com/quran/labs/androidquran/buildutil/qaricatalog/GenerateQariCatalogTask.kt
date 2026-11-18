package com.quran.labs.androidquran.buildutil.qaricatalog

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.NormalizeLineEndings

@CacheableTask
abstract class GenerateQariCatalogTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  @get:NormalizeLineEndings
  abstract val catalogFile: RegularFileProperty

  @get:Input
  abstract val moduleNamespace: Property<String>

  @get:OutputDirectory
  abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun generate() {
    val namespace = moduleNamespace.get()
    val source = QariCatalogGenerator.generate(catalogFile.get().asFile.readText(Charsets.UTF_8), namespace)
    val directory = outputDirectory.get().asFile
    val output = directory.resolve("${namespace.replace('.', '/')}/${QariCatalogGenerator.FILE_NAME}.kt")

    directory.walkTopDown().filter { it.isFile && it != output }.forEach { stale ->
      check(stale.delete()) { "Could not remove stale generated source $stale" }
    }

    if (!output.exists() || output.readText(Charsets.UTF_8) != source) {
      output.parentFile.mkdirs()
      output.writeText(source, Charsets.UTF_8)
    }
  }
}
