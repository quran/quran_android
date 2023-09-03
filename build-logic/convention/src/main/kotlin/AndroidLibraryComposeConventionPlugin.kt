import com.android.build.gradle.LibraryExtension
import com.quran.labs.androidquran.buildutil.applyAndroidCommon
import com.quran.labs.androidquran.buildutil.applyBoms
import com.quran.labs.androidquran.buildutil.applyKotlinCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("com.android.library")
        apply("org.jetbrains.kotlin.android")
      }

      extensions.configure<LibraryExtension> {
        applyAndroidCommon(target)
        buildFeatures.compose = true
        composeOptions.kotlinCompilerExtensionVersion = "1.5.3"
      }

      applyKotlinCommon()
      applyBoms()

      dependencies {
        // all compose projects need the runtime.
        // we can switch this to implementation instead of api once a fix is pushed for
        // https://issuetracker.google.com/issues/209688774.
        add("api", "androidx.compose.runtime:runtime")
      }
    }
  }
}
