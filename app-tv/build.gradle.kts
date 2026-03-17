plugins {
  id("quran.android.application.tv")
  alias(libs.plugins.kotlin.parcelize)
}

android {
  namespace = "com.quran.labs.androidquran.tv"

  defaultConfig {
    applicationId = "com.quran.labs.androidquran.tv"
    versionCode = 1
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    getByName("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
    }

    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
    }
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }

  packaging {
    resources {
      excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    }
  }
}

dependencies {
  // Only include common modules that are publishable (non-Android variants)
  // For initial TV implementation, we'll build standalone and integrate with common modules later

  // Kotlin
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)

  // Compose for TV
  implementation(libs.androidx.tv.foundation)
  implementation(libs.androidx.tv.material)

  // Compose
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.animation)

  // Navigation
  implementation(libs.androidx.navigation.compose)

  // Lifecycle
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
  implementation("androidx.activity:activity-compose:1.10.1")

  // Okio
  implementation(libs.okio)

  // Timber
  implementation(libs.timber)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)
}
