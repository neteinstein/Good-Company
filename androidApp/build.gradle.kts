plugins {
    alias(libs.plugins.android.application)
}
android {
    namespace = "com.neteinstein.goodcompany"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
}
