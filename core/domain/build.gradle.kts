// Pure Kotlin domain layer: models, repository interfaces, use cases.
// No Compose, no Android, no platform types — ever.
plugins {
    alias(libs.plugins.goodcompany.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
    }
}
