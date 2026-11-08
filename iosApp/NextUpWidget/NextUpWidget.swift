import SwiftUI
import WidgetKit

/// "Who's next" home-screen widget: the next birthday / big day / plan / reminder, and the friend most due a
/// catch-up, with "We met". Mirrors the Android widget.
///
/// Widgets can't run the shared Kotlin code, so the app writes a small JSON snapshot (`WidgetSnapshot` in
/// :core:domain) into the app group's UserDefaults (`IosBridgeKt.startWidgetSync`) and reloads the timelines.
/// Dates stay dates, so "Today / Tomorrow / In 3 days" is worked out here and stays right as days pass.
@main
struct NextUpWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "NextUpWidget", provider: SnapshotProvider()) { entry in
            NextUpView(entry: entry)
                .containerBackground(for: .widget) { Color(uiColor: .systemBackground) }
        }
        .configurationDisplayName(String(localized: "widget.title"))
        .description(String(localized: "widget.description"))
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Snapshot (mirrors WidgetSnapshot; kotlinx.serialization leaves out default values, hence the optionals)

struct Snapshot: Decodable {
    struct Next: Decodable {
        let kind: String
        let name: String?
        let kidName: String?
        let title: String?
        let date: String
    }

    struct CatchUp: Decodable {
        let friendId: String
        let firstName: String
    }

    let isPrivate: Bool?
    let next: Next?
    let catchUp: CatchUp?

    static func load() -> Snapshot? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "AppGroupId") as? String, !group.isEmpty,
              let json = UserDefaults(suiteName: group)?.string(forKey: "widget.snapshot"),
              let data = json.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }
}

struct SnapshotEntry: TimelineEntry {
    let date: Date
    let snapshot: Snapshot?
}

struct SnapshotProvider: TimelineProvider {
    func placeholder(in context: Context) -> SnapshotEntry { SnapshotEntry(date: Date(), snapshot: nil) }

    func getSnapshot(in context: Context, completion: @escaping (SnapshotEntry) -> Void) {
        completion(SnapshotEntry(date: Date(), snapshot: Snapshot.load()))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SnapshotEntry>) -> Void) {
        // Redraw just after midnight so "Tomorrow" becomes "Today"; the app also reloads whenever data changes.
        let midnight = Calendar.current.startOfDay(for: Date()).addingTimeInterval(24 * 60 * 60 + 60)
        completion(Timeline(entries: [SnapshotEntry(date: Date(), snapshot: Snapshot.load())], policy: .after(midnight)))
    }
}

// MARK: - View

/// Good Company red (#B4372F), the app's seed colour.
private let brand = Color(red: 0.706, green: 0.216, blue: 0.184)

struct NextUpView: View {
    let entry: SnapshotEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(String(localized: "widget.title"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(brand)
            if entry.snapshot?.isPrivate == true {
                Text(String(localized: "widget.private")).font(.subheadline)
            } else {
                nextView
                Spacer(minLength: 0)
                catchUpView
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetURL(URL(string: "goodcompany://open"))
    }

    @ViewBuilder private var nextView: some View {
        if let next = entry.snapshot?.next {
            Text(title(next))
                .font(.headline)
                .lineLimit(family == .systemSmall ? 3 : 2)
            if let date = Self.parse(next.date) {
                Text(whenText(date)).font(.caption).foregroundStyle(.secondary)
            }
        } else {
            Text(String(localized: "widget.nothing")).font(.headline)
        }
    }

    @ViewBuilder private var catchUpView: some View {
        if let catchUp = entry.snapshot?.catchUp {
            HStack {
                Text(String(localized: "widget.see \(catchUp.firstName)"))
                    .font(.subheadline)
                    .lineLimit(2)
                if family != .systemSmall, let url = URL(string: "goodcompany://met/\(catchUp.friendId)") {
                    Spacer(minLength: 4)
                    Link(destination: url) {
                        Text(String(localized: "widget.met"))
                            .font(.caption.weight(.semibold))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(brand.opacity(0.15), in: Capsule())
                    }
                }
            }
        } else if entry.snapshot != nil {
            Text(String(localized: "widget.caught_up")).font(.subheadline)
        }
    }

    private func title(_ next: Snapshot.Next) -> String {
        let name = next.name ?? ""
        switch next.kind {
        case "BIRTHDAY": return String(localized: "widget.birthday \(name)")
        case "KID_BIRTHDAY": return String(localized: "widget.kid_birthday \(next.kidName ?? "") \(name)")
        case "EVENT": return String(localized: "widget.event \(name) \(next.title ?? "")")
        default: return next.title ?? ""
        }
    }

    private func whenText(_ date: Date) -> String {
        let calendar = Calendar.current
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: entry.date), to: calendar.startOfDay(for: date)).day ?? 0
        switch days {
        case ..<1: return String(localized: "widget.today")
        case 1: return String(localized: "widget.tomorrow")
        default: return String(localized: "widget.in_days \(days)")
        }
    }

    /// "2026-11-08" (kotlinx LocalDate) in the user's calendar.
    static func parse(_ iso: String) -> Date? {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return Calendar.current.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }
}
