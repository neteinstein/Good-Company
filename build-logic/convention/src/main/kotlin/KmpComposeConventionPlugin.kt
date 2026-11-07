import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** [KmpLibraryConventionPlugin] + Compose Multiplatform and the shared UI baseline dependencies. */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply("goodcompany.kmp.library")
        pluginManager.apply(libs.pluginId("compose-multiplatform"))
        pluginManager.apply(libs.pluginId("compose-compiler"))

        extensions.configure<KotlinMultiplatformExtension> {
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
