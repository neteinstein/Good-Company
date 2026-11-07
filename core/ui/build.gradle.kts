// Design system: theme (light/dark/dynamic), shared components, formatting helpers,
// and the few UI-side platform hooks (permission prompts, file picker).
plugins {
    alias(libs.plugins.goodcompany.kmp.compose)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}
