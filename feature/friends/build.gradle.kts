plugins {
    alias(libs.plugins.goodcompany.kmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Back leaves a person's page's edit mode first (issue #90).
            implementation(libs.jetbrains.navigationevent.compose)
        }
    }
}
