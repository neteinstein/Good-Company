import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** [KmpLibraryConventionPlugin] + Compose Multiplatform and the shared UI baseline dependencies. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("goodcompany.kmp.library")
        pluginManager.apply(libs.pluginId("compose-multiplatform"))
        pluginManager.apply(libs.pluginId("compose-compiler"))

        // Each module gets its own generated `Res` class in `<namespace>.resources`, e.g.
        // `com.neteinstein.goodcompany.feature.friends.resources.Res`. Strings live in src/commonMain/composeResources/values*/.
        extensions.configure<ComposeExtension> {
            (this as ExtensionAware).extensions.configure<ResourcesExtension> {
                packageOfResClass = "$goodCompanyNamespace.resources"
                publicResClass = true
            }
        }

        extensions.configure<KotlinMultiplatformExtension> {
            // AGP 9's KMP library plugin turns Android resources off by default. Compose Multiplatform only copies
            // `composeResources` (strings, plurals, fonts) into the APK's assets when they're on; without this the
            // app has no resources on Android and crashes on its first stringResource()/Font().
            (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget> {
                androidResources { enable = true }
            }
            sourceSets.getByName("commonMain").dependencies {
                implementation(libs.lib("compose-runtime"))
                implementation(libs.lib("compose-foundation"))
                implementation(libs.lib("compose-ui"))
                implementation(libs.lib("compose-material3"))
                implementation(libs.lib("compose-material-icons-extended"))
                implementation(libs.lib("compose-components-resources"))
                implementation(libs.lib("compose-ui-tooling-preview"))
                implementation(libs.lib("jetbrains-lifecycle-runtime-compose"))
            }
        }
    }
}
