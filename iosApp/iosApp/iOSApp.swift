import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Kotlin/Native's Obj-C exporter renames top-level `initKoin()` to `doInitKoin()`
        // (names starting with "init" clash with Obj-C initializers).
        InitKoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
