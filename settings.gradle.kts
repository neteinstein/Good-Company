rootProject.name = "GoodCompany"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

// App entry points
include(":androidApp")
include(":composeApp")

// Core (shared across features)
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":core:testing")

// Features — one module each, never depending on each other
include(":feature:onboarding")
include(":feature:friends")
include(":feature:hangouts")
include(":feature:agenda")
include(":feature:ideas")
include(":feature:settings")
