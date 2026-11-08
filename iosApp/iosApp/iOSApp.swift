import SwiftUI
import WidgetKit
import ComposeApp

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Kotlin/Native's Obj-C exporter renames top-level `initKoin()` to `doInitKoin()`
        // (names starting with "init" clash with Obj-C initializers).
        InitKoinKt.doInitKoin()
        // Keeps the "Who's next" widget's snapshot (app group) fresh; WidgetCenter is Swift-only, so we pass it in.
        IosBridgeKt.startWidgetSync { WidgetCenter.shared.reloadAllTimelines() }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // goodcompany://met/<id> from the widget's "We met".
                .onOpenURL { url in _ = IosBridgeKt.handleAppUrl(url: url.absoluteString) }
        }
        .onChange(of: scenePhase) { _, phase in
            // Shares saved by the Share Extension wait in the app group until the app is opened.
            if phase == .active { IosBridgeKt.receivePendingShares() }
        }
    }
}
