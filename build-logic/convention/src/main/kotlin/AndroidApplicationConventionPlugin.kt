import com.android.build.api.dsl.ApplicationExtension
import com.quran.labs.androidquran.buildutil.applyAndroidCommon
import com.quran.labs.androidquran.buildutil.applyBoms
import com.quran.labs.androidquran.buildutil.applyKotlinCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class AndroidApplicationConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("com.android.application")
        apply("org.jetbrains.kotlin.android")
      }

      extensions.configure<ApplicationExtension> {
        applyAndroidCommon(target)
        defaultConfig.targetSdk = 32
      }

      applyKotlinCommon()
      applyBoms()
    }
  }
}
