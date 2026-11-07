import java.util.Properties

// The Android application entry point. AGP 9 forbids applying `com.android.application` and
// `org.jetbrains.kotlin.multiplatform` to the same module, so all shared UI/logic lives in :composeApp
// (a KMP library) and this module only holds the manifest, MainActivity, Application, icons and Firebase.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services) apply false
}

// google-services.json is a gitignored secret. CI copies google-services.json.ci in for build validation;
// release builds get the real file from a CI secret. Without either, Firebase is simply skipped.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

val signingProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "com.neteinstein.goodcompany"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.neteinstein.goodcompany"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"
    }

    // Per-app language picker (Android 13+): generates the locale list from the res/values-* folders.
    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        if (signingProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink, optimize and obfuscate.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Same as release, plus READ_CALL_LOG (src/sideload/AndroidManifest.xml).
        // Google Play restricts that permission to default dialer apps, so this build is for sideloading only.
        create("sideload") {
            initWith(getByName("release"))
            applicationIdSuffix = ".sideload"
            versionNameSuffix = "-sideload"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        abortOnError = true
        checkDependencies = true
        warningsAsErrors = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.data)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    // compose-runtime for setContent { App() }
    implementation(libs.compose.runtime)
}
