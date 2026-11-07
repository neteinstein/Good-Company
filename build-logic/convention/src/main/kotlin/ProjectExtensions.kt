import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog, usable from precompiled convention plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

internal fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId

/**
 * Derives a stable Android namespace from the Gradle project path,
 * e.g. `:feature:friends` → `com.neteinstein.goodcompany.feature.friends`.
 */
internal val Project.goodCompanyNamespace: String
    get() = "com.neteinstein.goodcompany." + path.removePrefix(":").replace(':', '.').replace('-', '_')
