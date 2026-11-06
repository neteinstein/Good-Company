import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Base convention for every shared KMP module.
 *
 * Targets: Android (AGP 9 `com.android.kotlin.multiplatform.library`), iosArm64, iosSimulatorArm64, wasmJs.
 * iosX64 is intentionally absent — it was removed upstream.
 *
 * Also registers a `testDebugUnitTest` alias for the Android host (JVM) tests so that
 * `./gradlew testDebugUnitTest` runs every module's commonTest on the JVM, exactly as it does
 * for a classic Android module.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    @OptIn(ExperimentalWasmDsl::class)
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs.pluginId("kotlin-multiplatform"))
        pluginManager.apply(libs.pluginId("android-kotlin-multiplatform-library"))
        pluginManager.apply(libs.pluginId("kover"))

        extensions.configure<KotlinMultiplatformExtension> {
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                namespace = goodCompanyNamespace
                compileSdk = libs.version("android-compileSdk").toInt()
                minSdk = libs.version("android-minSdk").toInt()
                withHostTest {
                    isReturnDefaultValues = true
                }
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(libs.version("jvmTarget")))
                }
            }

            iosArm64()
            iosSimulatorArm64()

            wasmJs {
                browser()
            }

            applyDefaultHierarchyTemplate()

            compilerOptions {
                optIn.addAll(
                    "kotlin.time.ExperimentalTime",
                    "kotlinx.coroutines.ExperimentalCoroutinesApi",
                )
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }

            sourceSets.getByName("commonTest").dependencies {
                implementation(libs.lib("kotlin-test"))
                implementation(libs.lib("kotlinx-coroutines-test"))
            }
        }

        // `testDebugUnitTest` → Android host tests (JVM). Keeps one command working repo-wide.
        tasks.register("testDebugUnitTest") {
            group = "verification"
            description = "Runs commonTest + androidHostTest on the JVM (alias of testAndroidHostTest)."
            dependsOn("testAndroidHostTest")
        }

    }
}
