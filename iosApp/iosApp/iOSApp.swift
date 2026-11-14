import SwiftUI
import UIKit
import WidgetKit
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Kotlin/Native's Obj-C exporter renames top-level `initKoin()` to `doInitKoin()`
        // (names starting with "init" clash with Obj-C initializers).
        InitKoinKt.doInitKoin()
        // Keeps the "Who's next" widget's snapshot (app group) fresh; WidgetCenter is Swift-only, so we pass it in.
        IosBridgeKt.startWidgetSync { WidgetCenter.shared.reloadAllTimelines() }
        // Keeps the app-icon quick actions ("Log a meetup" + recent people) ready for the next trip to the background.
        IosBridgeKt.startShortcutSync()
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
            if phase == .background { QuickActions.publish() }
        }
    }
}

/// App-icon quick actions: titles come from Kotlin already localised, so there are no Swift strings here.
enum QuickActions {
    private static let urlKey = "url"

    static func publish() {
        UIApplication.shared.shortcutItems = IosBridgeKt.shortcutItems().map { item in
            UIApplicationShortcutItem(
                type: item.type,
                localizedTitle: item.title,
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(systemImageName: item.isPerson ? "person.crop.circle.fill" : "person.2.fill"),
                userInfo: [urlKey: item.url as NSString]
            )
        }
    }

    /// Opens the Log sheet through `goodcompany://log[/<id>]`; the app lock (when on) still comes first.
    @discardableResult
    static func handle(_ item: UIApplicationShortcutItem) -> Bool {
        guard let url = item.userInfo?[urlKey] as? String else { return false }
        return IosBridgeKt.handleAppUrl(url: url)
    }
}

/// Only here to give every scene [QuickActionSceneDelegate]: SwiftUI hands quick actions to the scene delegate.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = QuickActionSceneDelegate.self
        return configuration
    }
}

final class QuickActionSceneDelegate: NSObject, UIWindowSceneDelegate {
    // Cold start: the quick action that launched the app.
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem { QuickActions.handle(item) }
    }

    // Already running: a quick action picked from the icon.
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(QuickActions.handle(shortcutItem))
    }
}
