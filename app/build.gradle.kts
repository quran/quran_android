import net.ltgt.gradle.errorprone.ErrorProneOptions
import java.util.Locale

plugins {
  id("quran.android.application")
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.ksp)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.metro)
  alias(libs.plugins.kover)
}

// whether or not to use Firebase - Firebase is enabled by default, and is only disabled for
// providing apks for open source distribution stores.
val useFirebase = !project.hasProperty("disableFirebase")

// only want to apply the Firebase plugin if we're building a release build. moving this to the
// release build type won't work, since debug builds would also implicitly get the plugin.
if (getGradle().startParameter.taskRequests.toString().contains("Release") && useFirebase) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
}

android {
  namespace = "com.quran.labs.androidquran"

  defaultConfig {
    versionCode = 3610
    versionName = "3.6.1"
    testInstrumentationRunner = "com.quran.labs.androidquran.core.QuranTestRunner"
  }

  androidResources {
    // Indonesian is still in instead of id due to https://issuetracker.google.com/issues/36911507
    @Suppress("UnstableApiUsage")
    localeFilters += listOf(
      "ar", "az", "bg", "bn", "bs", "cs", "da", "de", "el", "es", "et", "fa",
      "fi", "fr", "hi", "hr", "hu", "in", "it", "ja", "kk", "ko", "ku", "lt",
      "lv", "ms", "nl", "pl", "ps", "pt", "ro", "ru", "sk", "sl", "sq", "sr",
      "sv", "th", "tr", "ug", "uk", "ur", "uz", "vi", "zh"
    )
  }

  dependenciesInfo {
    // only keep dependency info block for builds with Firebase
    includeInApk = useFirebase
    includeInBundle = useFirebase
  }

  buildFeatures.buildConfig = true

  signingConfigs {
    create("release") {
      storeFile = file((project.property("STORE_FILE") as String))
      storePassword = project.property("STORE_PASSWORD") as String
      keyAlias = project.property("KEY_ALIAS") as String
      keyPassword = project.property("KEY_PASSWORD") as String
    }
  }

  flavorDimensions += listOf("pageType")
  productFlavors {
    create("madani") {
      applicationId = "com.quran.labs.androidquran"
    }
  }

  buildTypes {
    create("beta") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
      signingConfig = signingConfigs.getByName("release")
      versionNameSuffix = "-beta"
      matchingFallbacks += listOf("debug", "release")
    }

    getByName("debug") {
      applicationIdSuffix = ".debug"
      versionNameSuffix = "-debug"
      matchingFallbacks += "release"
    }

    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
      signingConfig = signingConfigs.getByName("release")
    }
  }

  applicationVariants.all {
    resValue("string", "authority", "${applicationId}.data.QuranDataProvider")
    resValue("string", "file_authority", "${applicationId}.fileprovider")
    if (applicationId.endsWith("debug")) {
      mergedFlavor.manifestPlaceholders["app_debug_label"] =
        "Quran ${flavorName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}"
    }
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.testLogging {
          events("passed", "skipped", "failed", "standardOut", "standardError")
          showStandardStreams = true
        }
      }
    }
  }

  packaging {
    resources {
      excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST")
    }
  }
}

// required so that Errorprone doesn't look at generated files
afterEvaluate {
  tasks.withType<JavaCompile>().configureEach {
    (options as ExtensionAware).extensions.configure<ErrorProneOptions> {
      excludedPaths.set(".*/build/generated/.*")
      disableWarningsInGeneratedCode = true
    }
  }
}

if (File(rootDir, "extras/extras.gradle").exists()) {
  apply(from = File(rootDir, "extras/extras.gradle"))
} else {
  apply(from = "pluggable.gradle")
}

dependencies {
  implementation(project(":common:analytics"))
  implementation(project(":common:audio"))
  implementation(project(":common:bookmark"))
  implementation(project(":common:data"))
  implementation(project(":common:di"))
  implementation(project(":common:download"))
  implementation(project(":common:networking"))
  implementation(project(":common:pages"))
  implementation(project(":common:preference"))
  implementation(project(":common:reading"))
  implementation(project(":common:recitation"))
  implementation(project(":common:search"))
  implementation(project(":common:toolbar"))
  implementation(project(":common:translation"))
  implementation(project(":common:upgrade"))
  implementation(project(":common:ui:core"))

  implementation(project(":feature:audio"))
  implementation(project(":feature:audiobar"))
  implementation(project(":feature:downloadmanager"))
  implementation(project(":feature:qarilist"))

  // android auto support
  implementation(project(":feature:autoquran"))

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.retrofit)
  implementation(libs.converter.moshi)

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.media)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.localbroadcastmanager)
  implementation(libs.androidx.preference.ktx)
  implementation(libs.androidx.recyclerview)
  implementation(libs.material)
  implementation(libs.androidx.swiperefreshlayout)
  implementation(libs.androidx.window)

  // compose
  implementation(libs.compose.ui)

  // okio
  implementation(libs.okio)

  // rx
  implementation(libs.rxjava)
  implementation(libs.rxandroid)

  // analytics
  debugImplementation(project(":feature:analytics-noop"))
  add("betaImplementation", project(":feature:analytics-noop"))
  if (useFirebase) {
    releaseImplementation(project(":feature:firebase-analytics"))
  } else {
    releaseImplementation(project(":feature:analytics-noop"))
  }

  // workmanager
  implementation(libs.androidx.work.runtime.ktx)

  implementation(libs.okhttp)

  implementation(libs.moshi)
  ksp(libs.moshi.codegen)

  implementation(libs.insetter)
  implementation(libs.timber)
  debugImplementation(libs.leakcanary.android)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.mockito.core)
  testImplementation(libs.okhttp.mockserver)
  testImplementation(libs.junit.ktx)
  testImplementation(libs.robolectric)
  testImplementation(libs.espresso.core)
  testImplementation(libs.espresso.intents)
  testImplementation(libs.turbine)
  testImplementation(libs.kotlinx.coroutines.test)

  errorprone(libs.errorprone.core)

  // Number Picker
  implementation(libs.number.picker)

}

// Kover coverage configuration
kover {
  reports {
    filters {
      excludes {
        // Exclude generated code
        classes(
          "*_Factory",
          "*_Factory\$*",
          "*_MembersInjector",
          "*Module",
          "*Module\$*",
          "*Binding*",
          "*_Impl",
          "*_Impl\$*",
          "*.BuildConfig",
          "*.databinding.*",
          "*.R",
          "*.R\$*",
          "*Hilt*",
          "*_HiltModules*",
          "*_ComponentTreeDeps*",
          "*_Provide*Factory*"
        )
        packages(
          "*.di.*",
          "*.generated.*",
          "dagger.hilt.*",
          "hilt_aggregated_deps.*"
        )
        annotatedBy(
          "dagger.*",
          "javax.inject.*",
          "androidx.compose.ui.tooling.preview.Preview"
        )
      }
    }
  }
}
