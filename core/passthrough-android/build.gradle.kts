plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.pratclot.cafes.core.passthrough.android"
  compileSdk = 37

  defaultConfig { minSdk = 24 }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { jvmToolchain(17) }

dependencies {
  api(project(":core:passthrough"))

  api(libs.androidx.core.ktx)

  api(platform(libs.androidx.compose.bom))
  api(libs.androidx.activity.compose)
  api(libs.androidx.lifecycle.runtime.compose)
  api(libs.androidx.lifecycle.runtime.ktx)
  api(libs.androidx.lifecycle.viewmodel.compose)
  api(libs.androidx.compose.animation)
  api(libs.androidx.compose.foundation)
  api(libs.androidx.compose.ui)
  api(libs.androidx.compose.ui.tooling.preview)
  api(libs.androidx.compose.material3)

  api(libs.androidx.navigation3.runtime)
  api(libs.androidx.navigation3.ui)
  api(libs.androidx.lifecycle.viewmodel.navigation3)
  api(libs.androidx.compose.material3.adaptive)
  api(libs.androidx.compose.material3.adaptive.layout)
  api(libs.androidx.compose.material3.adaptive.navigation3)
}
