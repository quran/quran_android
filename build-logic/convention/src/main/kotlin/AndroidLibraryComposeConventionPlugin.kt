import com.android.build.gradle.LibraryExtension
import com.quran.labs.androidquran.buildutil.applyAndroidCommon
import com.quran.labs.androidquran.buildutil.applyBoms
import com.quran.labs.androidquran.buildutil.applyKotlinCommon
import com.quran.labs.androidquran.buildutil.withLibraries
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        withLibraries { libs ->
          apply(libs.plugins.android.library.get().pluginId)
          apply(libs.plugins.kotlin.android.get().pluginId)
        }
      }

      extensions.configure<LibraryExtension> {
        applyAndroidCommon(target)
        buildFeatures.compose = true
        withLibraries { libs ->
          composeOptions.kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
        }

        (this as ExtensionAware).extensions.configure<KotlinJvmOptions>("kotlinOptions") {
          freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
          )
        }
      }

      applyKotlinCommon()
      applyBoms()

      dependencies {
        // all compose projects need the runtime.
        // we can switch this to implementation instead of api once a fix is pushed for
        // https://issuetracker.google.com/issues/209688774.
        withLibraries { libs ->
          add("api", libs.compose.runtime)
        }
      }
    }
  }
}
