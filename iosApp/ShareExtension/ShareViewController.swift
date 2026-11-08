import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// "Share → Good Company" from Instagram, LinkedIn, Photos, Safari…
///
/// Extensions can't run the shared Kotlin code, so this only saves what was shared into the app group's
/// `ShareInbox` folder (`<timestamp>-<uuid>.json`, plus a downscaled `.jpg` for images) and says so.
/// The app picks it up the next time it becomes active (`IosBridgeKt.receivePendingShares`) and asks which friend
/// it's about.
final class ShareViewController: UIViewController {
    private static let maxSide: CGFloat = 1568

    override func viewDidLoad() {
        super.viewDidLoad()
        Task { @MainActor in
            let saved = await saveShare()
            let done = UIHostingController(rootView: SavedView(saved: saved) { [weak self] in
                self?.extensionContext?.completeRequest(returningItems: nil)
            })
            addChild(done)
            done.view.frame = view.bounds
            done.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            view.addSubview(done.view)
            done.didMove(toParent: self)
        }
    }

    private func saveShare() async -> Bool {
        var text: String?
        var url: String?
        var image: UIImage?
        let providers = (extensionContext?.inputItems as? [NSExtensionItem] ?? []).flatMap { $0.attachments ?? [] }
        for provider in providers {
            if url == nil, provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
               let item = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) {
                url = (item as? URL)?.absoluteString ?? (item as? String)
            } else if text == nil, provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
                      let item = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) {
                text = item as? String
            } else if image == nil, provider.hasItemConformingToTypeIdentifier(UTType.image.identifier),
                      let item = try? await provider.loadItem(forTypeIdentifier: UTType.image.identifier, options: nil) {
                if let img = item as? UIImage {
                    image = img
                } else if let fileURL = item as? URL, let data = try? Data(contentsOf: fileURL) {
                    image = UIImage(data: data)
                } else if let data = item as? Data {
                    image = UIImage(data: data)
                }
            }
        }
        guard text != nil || url != nil || image != nil, let inbox = Self.inboxURL() else { return false }
        let id = "\(Int(Date().timeIntervalSince1970 * 1000))-\(UUID().uuidString)"
        var entry: [String: String] = [:]
        entry["text"] = text
        entry["url"] = url
        if let image, let jpeg = Self.downscaled(image).jpegData(compressionQuality: 0.85) {
            let name = "\(id).jpg"
            guard (try? jpeg.write(to: inbox.appendingPathComponent(name))) != nil else { return false }
            entry["image"] = name
            entry["mediaType"] = "image/jpeg"
        }
        guard let json = try? JSONSerialization.data(withJSONObject: entry) else { return false }
        return (try? json.write(to: inbox.appendingPathComponent("\(id).json"))) != nil
    }

    private static func inboxURL() -> URL? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "AppGroupId") as? String, !group.isEmpty,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) else { return nil }
        let inbox = container.appendingPathComponent("ShareInbox", isDirectory: true)
        try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        return inbox
    }

    private static func downscaled(_ image: UIImage) -> UIImage {
        let longest = max(image.size.width, image.size.height)
        guard longest > maxSide else { return image }
        let scale = maxSide / longest
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
    }
}

private struct SavedView: View {
    let saved: Bool
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: saved ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundStyle(saved ? Color.accentColor : Color.orange)
            Text(saved ? String(localized: "share.saved.title") : String(localized: "share.failed.title"))
                .font(.title3.bold())
                .multilineTextAlignment(.center)
            Text(saved ? String(localized: "share.saved.body") : String(localized: "share.failed.body"))
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button(String(localized: "share.done"), action: onDone)
                .buttonStyle(.borderedProminent)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(uiColor: .systemBackground))
    }
}
