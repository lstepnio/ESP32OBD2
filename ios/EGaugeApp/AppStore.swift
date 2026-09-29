import Combine
import EGaugeCore
import Foundation

@MainActor
final class AppStore: ObservableObject {
    @Published private(set) var collection: ProfileCollection
    @Published private(set) var storageError: String?
    @Published var advancedTools: Bool {
        didSet { UserDefaults.standard.set(advancedTools, forKey: "advanced-tools") }
    }
    @Published var gaugeName: String {
        didSet { UserDefaults.standard.set(gaugeName, forKey: "gauge-name") }
    }

    private let fileURL: URL

    init(directory: URL? = nil) {
        var root = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        #if DEBUG
        if let session = ProcessInfo.processInfo.environment["EGAUGE_UI_TEST_SESSION"],
           UUID(uuidString: session) != nil {
            root = root.appendingPathComponent("UITests", isDirectory: true)
                .appendingPathComponent(session, isDirectory: true)
        }
        #endif
        fileURL = root.appendingPathComponent("eGauge", isDirectory: true).appendingPathComponent("profiles.json")
        advancedTools = UserDefaults.standard.bool(forKey: "advanced-tools")
        gaugeName = UserDefaults.standard.string(forKey: "gauge-name") ?? "My gauge"
        if FileManager.default.fileExists(atPath: fileURL.path) {
            do {
                collection = try ProfileCodec.decode(Data(contentsOf: fileURL))
                storageError = nil
            } catch {
                collection = ProfileCollection()
                storageError = "Saved profiles could not be read. Editing is paused so the original file stays intact."
            }
        } else {
            collection = ProfileCollection()
            storageError = nil
        }
    }

    var active: VehicleProfile { collection.profiles[collection.activeIndex] }
    var canEdit: Bool { storageError == nil }

    func selectProfile(_ id: String) {
        update { collection in
            guard collection.profiles.contains(where: { $0.id == id }) else { return }
            collection.activeId = id
        }
    }

    func addProfile(name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard canAddProfile(named: trimmed) else { return }
        update { collection in
            let id = "vehicle-\(UUID().uuidString.lowercased())"
            collection.profiles.append(VehicleProfile(id: id, name: trimmed))
            collection.activeId = id
        }
    }

    func canAddProfile(named name: String) -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return (1...32).contains(trimmed.count) && collection.profiles.count < 8 &&
            !collection.profiles.contains { $0.name.localizedCaseInsensitiveCompare(trimmed) == .orderedSame }
    }

    func updateDraft(_ change: (inout GaugeDraft) -> Void) {
        update { collection in change(&collection.profiles[collection.activeIndex].draft) }
    }

    private func update(_ change: (inout ProfileCollection) -> Void) {
        guard canEdit else { return }
        var next = collection
        change(&next)
        do {
            try FileManager.default.createDirectory(at: fileURL.deletingLastPathComponent(),
                                                    withIntermediateDirectories: true)
            try ProfileCodec.encode(next).write(to: fileURL, options: .atomic)
            collection = next
        } catch {
            storageError = "Changes could not be saved. Check free space before editing again."
        }
    }
}
