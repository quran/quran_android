import com.android.build.api.dsl.LibraryExtension
import com.quran.labs.androidquran.buildutil.applyAndroidCommon
import com.quran.labs.androidquran.buildutil.applyBoms
import com.quran.labs.androidquran.buildutil.applyJavaCommon
import com.quran.labs.androidquran.buildutil.applyKotlinCommon
import com.quran.labs.androidquran.buildutil.disableDebugVariant
import com.quran.labs.androidquran.buildutil.withLibraries
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        withLibraries { libs ->
          apply(libs.plugins.android.library.get().pluginId)
        }
      }

      extensions.configure<LibraryExtension> {
        applyAndroidCommon(target)
        disableDebugVariant()
      }

      applyJavaCommon()
      applyKotlinCommon()
      applyBoms()
    }
  }
}
