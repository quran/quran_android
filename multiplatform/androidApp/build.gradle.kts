plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
android { namespace="org.quran.app"; compileSdk=36; compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
 defaultConfig { applicationId="org.quran.app.redesign"; minSdk=26; targetSdk=36; versionCode=1;versionName="0.1.0"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose=true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { implementation(project(":composeApp"));implementation(project(":core:data"));implementation(project(":core:domain"));implementation("androidx.activity:activity-compose:1.11.0")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.11.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:core-ktx:1.6.1")
 androidTestImplementation(project(":core:model"))
 androidTestImplementation(project(":feature:memorization"))
}
