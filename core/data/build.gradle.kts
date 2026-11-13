// Data layer: repository implementations, persistence and platform data sources
// (contacts, location, notifications, communication logs, AI client).
plugins {
    alias(libs.plugins.goodcompany.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    android {
        // Notification icon + channel name live in src/androidMain/res.
        androidResources { enable = true }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.sqldelight.async)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        wasmJsMain.dependencies {
            implementation(libs.ktor.client.js)
            implementation(libs.sqldelight.web.worker.driver)
            implementation(libs.kotlinx.browser)
            // The SQLite WebAssembly build and our OPFS worker (a local npm package, bundled by webpack).
            implementation(npm("@sqlite.org/sqlite-wasm", libs.versions.sqlite.wasm.get()))
            implementation(npm("good-company-sqlite-worker", file("src/wasmJsMain/sqlite-worker")))
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
    }
}

sqldelight {
    databases {
        create("GoodCompanyDatabase") {
            packageName.set("com.neteinstein.goodcompany.core.data.db")
            // The web worker driver is asynchronous, so every query is generated as a suspend call on all platforms.
            generateAsync.set(true)
            // Numbered .sqm migrations are checked against the committed schema snapshots in src/commonMain/sqldelight/databases.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
