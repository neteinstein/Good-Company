import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Shared app shell: root App() composable, navigation, theme hookup and the single Koin AppModule.
// Produces the Android library consumed by :androidApp, the `ComposeApp` iOS framework
// and the wasmJs web executable.
plugins {
    alias(libs.plugins.goodcompany.kmp.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.ui)
            implementation(projects.feature.onboarding)
            implementation(projects.feature.friends)
            implementation(projects.feature.hangouts)
            implementation(projects.feature.agenda)
            implementation(projects.feature.ideas)
            implementation(projects.feature.settings)

            implementation(project.dependencies.platform(libs.koin.bom))
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.koin.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}
