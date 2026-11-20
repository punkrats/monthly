// AGP compiles Kotlin itself; the Kotlin plugin is listed only to put
// its version on the build classpath.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
