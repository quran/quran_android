import java.io.FileInputStream
import java.util.Properties

plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.anvil)
}

android {
  namespace  = "com.quran.mobile.feature.sync"
  buildFeatures.buildConfig = true

  val properties = Properties()
  val propertiesFile = project.projectDir.resolve("oauth.properties")

  if (propertiesFile.exists()) {
    properties.load(FileInputStream(propertiesFile))
  }

  defaultConfig {
    buildConfigField(
      "String",
      "CLIENT_ID",
      "\"${properties.getProperty("client_id", "")}\""
    )
    buildConfigField(
      "String",
      "DISCOVERY_URI",
      "\"${properties.getProperty("discovery_uri", "")}\""
    )
    buildConfigField(
      "String",
      "SCOPES",
      "\"${properties.getProperty("scopes", "")}\""
    )
    buildConfigField(
      "String",
      "REDIRECT_URI",
      "\"${properties.getProperty("redirect_uri", "")}\""
    )
  }
}

anvil {
  useKsp(contributesAndFactoryGeneration = true, componentMerging = true)
  generateDaggerFactories.set(true)
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))
  api(project(":common:sync"))
  implementation(project(":common:ui:core"))

  // androidx
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)

  // compose
  implementation(libs.compose.animation)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  debugImplementation(libs.compose.ui.tooling)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // molecule
  implementation(libs.molecule)

  // app auth library
  implementation(libs.appauth)

  // dagger
  implementation(libs.dagger.runtime)

  // timber
  implementation(libs.timber)
}
