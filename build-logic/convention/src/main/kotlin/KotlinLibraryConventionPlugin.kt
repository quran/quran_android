import com.quran.labs.androidquran.buildutil.applyBoms
import org.gradle.api.Plugin
import org.gradle.api.Project

class KotlinLibraryConventionPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    with(target) {
      with(pluginManager) {
        apply("kotlin")
      }

      applyBoms()
    }
  }
}
