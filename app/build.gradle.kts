import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "app.monthly"
  compileSdk {
    version = release(37) { minorApiLevel = 2 }
  }

  defaultConfig {
    applicationId = "app.monthly"
    minSdk = 26
    targetSdk = 35
    versionCode = 2
    versionName = "0.2.0"
  }

  // The release key lives outside the repo; its path and password come
  // from ~/.gradle/gradle.properties. Without them the release APK is
  // left unsigned.
  fun property(name: String) =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }

  val release = property("MONTHLY_KEYSTORE")?.let { path ->
    signingConfigs.create("release") {
      val password = property("MONTHLY_KEYSTORE_PASSWORD")
      storeFile = file(path)
      storePassword = password
      keyAlias = property("MONTHLY_KEY_ALIAS")
      keyPassword = password
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = release
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  // Robolectric needs the merged manifest and resources.
  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.compose.icons.extended)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.junit)
  testImplementation(platform(libs.compose.bom))
  testImplementation(libs.compose.ui.test.junit4)
  debugImplementation(libs.compose.ui.test.manifest)
}
