import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.quran.labs.androidquran.buildutil.qaricatalog.GenerateQariCatalogTask
import com.quran.labs.androidquran.buildutil.qaricatalog.QariCatalogExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

class QariCatalogPlugin : Plugin<Project> {
  @Suppress("UnstableApiUsage")
  override fun apply(target: Project) {
    val rootDirectory = target.isolated.rootProject.projectDirectory
    val providers = target.providers
    val extension = target.extensions.create<QariCatalogExtension>("qariCatalog")
    val catalog = extension.overrideProperty
      .flatMap { providers.gradleProperty(it) }
      .map { path ->
        require(path.isNotBlank()) { "The qari catalog override must name a JSON file" }
        rootDirectory.file(path)
      }
      .orElse(extension.catalogFile)
    val generatedDirectory = target.layout.buildDirectory.dir("generated/qariCatalog/kotlin")
    val generate = target.tasks.register<GenerateQariCatalogTask>("generateQaris") {
      group = "build"
      description = "Generates reciters from the selected JSON catalog."
      catalogFile.set(catalog)
      outputDirectory.set(generatedDirectory)
    }

    target.pluginManager.withPlugin("com.android.library") {
      val androidComponents = target.extensions.getByType<LibraryAndroidComponentsExtension>()
      androidComponents.finalizeDsl { android ->
        val namespace = checkNotNull(android.namespace) { "An Android namespace is required to generate qaris" }
        generate.configure { moduleNamespace.set(namespace) }
      }
      androidComponents.onVariants { variant ->
        checkNotNull(variant.sources.kotlin).addGeneratedSourceDirectory(
          generate,
          GenerateQariCatalogTask::outputDirectory
        )
      }
    }
  }
}
