import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `kotlin-dsl`
}

group = "com.quran.labs.androidquran.buildlogic"

dependencies {
  compileOnly(libs.android.gradlePlugin)
  compileOnly(libs.kotlin.gradlePlugin)
  compileOnly(libs.compose.compiler.gradlePlugin)
  compileOnly(libs.sqldelight.gradlePlugin)
  compileOnly(libs.ksp.gradlePlugin)
  implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

gradlePlugin {
  plugins {
    register("androidApplication") {
      id = "quran.android.application"
      implementationClass = "AndroidApplicationConventionPlugin"
    }

    register("androidLibrary") {
      id = "quran.android.library.android"
      implementationClass = "AndroidLibraryConventionPlugin"
    }

    register("androidComposeLibrary") {
      id = "quran.android.library.compose"
      implementationClass = "AndroidLibraryComposeConventionPlugin"
    }

    register("kotlinLibrary") {
      id = "quran.android.library"
      implementationClass = "KotlinLibraryConventionPlugin"
    }
  }
}
