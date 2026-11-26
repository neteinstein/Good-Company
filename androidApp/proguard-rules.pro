# R8 rules for Good Company (release + sideload). Library rules (kotlinx.serialization, Ktor, WorkManager,
# Koin, Compose, Firebase) ship as consumer rules inside their AARs/JARs; only app-specific bits go here.

# Readable crash stack traces while keeping obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Persisted JSON + type-safe navigation routes are @Serializable. kotlinx.serialization's bundled rules keep
# generated serializers; keep our model package names stable too so stored data survives refactors of R8 output.
-keepclassmembers @kotlinx.serialization.Serializable class org.goodcompany.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation routes keep their names, so "Destination with route … cannot be found" names the route in a release crash
# (issue #114: it said "ln5", which was TagsRoute, never registered). AppRoutesTest checks every route is registered.
-keepnames class * implements org.goodcompany.app.ui.navigation.AppRoute

# WorkManager instantiates workers reflectively by class name (also covered by WorkManager's own rules).
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Ktor optional JVM integrations that aren't on Android.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn io.ktor.util.debug.**
