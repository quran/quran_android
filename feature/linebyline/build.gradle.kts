plugins {
  id("quran.android.library.compose")
  alias(libs.plugins.metro)
}

android.namespace = "com.quran.labs.androidquran.extra.feature.linebyline"
android.testOptions.unitTests.isIncludeAndroidResources = true

androidComponents {
  onVariants { variant ->
    @Suppress("UnstableApiUsage")
    (variant as? com.android.build.api.variant.HasUnitTest)?.unitTest?.manifestPlaceholders?.putAll(
      mapOf(
        "oidcRedirectScheme" to "com.quran.labs.androidquran.test",
        "oidcRedirectHost" to "callback",
        "oidcPostLogoutRedirectScheme" to "com.quran.labs.androidquran.test",
        "oidcPostLogoutRedirectHost" to "callback"
      )
    )
  }
}

dependencies {
  implementation(project(":common:di"))
  implementation(project(":common:data"))
  implementation(project(":common:audio"))
  implementation(project(":common:bookmark"))
  implementation(project(":common:reading"))
  implementation(project(":common:analytics"))
  implementation(project(":common:drawing"))
  implementation(project(":common:linebyline:ui"))
  implementation(project(":common:ui:core"))
  // have to be api, otherwise can't add classes to the correct components
  api(project(":common:mapper:imlaei"))
  api(project(":common:linebyline:data"))

  implementation(libs.androidx.fragment.ktx)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.timber)

  implementation(libs.compose.ui)
  implementation(libs.compose.material)

  // implementation but removed for release builds
  implementation(libs.compose.ui.tooling)
  implementation(libs.compose.ui.tooling.preview)

  implementation(libs.kotlinx.collections.immutable)

  // coroutines
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  // testing
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}
