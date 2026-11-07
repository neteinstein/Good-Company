// Root build: declares plugins (apply false) so every module resolves the same versions,
// plus repo-wide formatting (Spotless/ktlint) and aggregated coverage (Kover).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "build-logic/**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
}

dependencies {
    // Aggregated coverage report across every module that runs JVM tests.
    kover(projects.core.domain)
    kover(projects.core.data)
    kover(projects.core.ui)
    kover(projects.feature.onboarding)
    kover(projects.feature.friends)
    kover(projects.feature.hangouts)
    kover(projects.feature.agenda)
    kover(projects.feature.ideas)
    kover(projects.feature.settings)
    kover(projects.composeApp)
}

kover {
    reports {
        verify {
            // CI fails if aggregated line coverage drops below this. Raise it as coverage grows; never lower it to get green.
            rule("Minimum line coverage") {
                minBound(65)
            }
        }
        filters {
            excludes {
                // Generated code and Compose UI aren't meaningfully covered by JVM unit tests.
                classes("*.BuildConfig", "*ComposableSingletons*", "*.generated.resources.*")
                annotatedBy("androidx.compose.runtime.Composable")
            }
        }
    }
}
