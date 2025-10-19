import com.android.build.gradle.LibraryExtension
import com.quran.labs.androidquran.buildutil.applyAndroidCommon
import com.quran.labs.androidquran.buildutil.applyBoms
import com.quran.labs.androidquran.buildutil.applyComposeCommon
import com.quran.labs.androidquran.buildutil.applyJavaCommon
import com.quran.labs.androidquran.buildutil.applyKotlinCommon
import com.quran.labs.androidquran.buildutil.disableDebugVariant
import com.quran.labs.androidquran.buildutil.withLibraries
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        withLibraries { libs ->
          apply(libs.plugins.android.library.get().pluginId)
          apply(libs.plugins.kotlin.android.get().pluginId)
          apply(libs.plugins.compose.compiler.get().pluginId)
        }
      }

      extensions.configure<LibraryExtension> {
        applyAndroidCommon(target)
        applyComposeCommon(target)
        disableDebugVariant()
      }

      applyJavaCommon()
      applyKotlinCommon()
      applyBoms()
    }
  }
}
