// Test doubles (in-memory repositories, fixed clock, fakes) shared by every module's commonTest.
plugins {
    alias(libs.plugins.goodcompany.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            api(libs.kotlinx.coroutines.test)
            api(libs.kotlin.test)
        }
    }
}
