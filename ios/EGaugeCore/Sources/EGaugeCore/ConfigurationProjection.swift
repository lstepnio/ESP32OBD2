import Foundation

public struct ProjectedPage: Equatable, Sendable {
    public let id: String
    public let name: String
    public let renderer: String
    public let pidIds: [String]
}

public struct ProjectedAlert: Equatable, Sendable {
    public let id: String
    public let pidId: String
    public let direction: String
    public let warning: Int
    public let critical: Int
}

public struct ConfigurationProjection: Equatable, Sendable {
    public let pages: [ProjectedPage]
    public let alerts: [ProjectedAlert]
    public let bytes: Data

    public static func == (lhs: Self, rhs: Self) -> Bool {
        lhs.pages == rhs.pages && lhs.alerts == rhs.alerts && lhs.bytes == rhs.bytes
    }
}

/// Creates the same semantic document that Android reviews before a transfer.
/// The SHA-256 for a transfer must be calculated over `bytes` exactly as returned.
public enum ConfigurationProjector {
    public enum Failure: Error, Equatable { case blocked([String]), invalidTemplate }

    private static let definitionIDs = [
        "rpm": "engine.rpm", "coolant": "engine.coolant", "speed": "vehicle.speed",
        "load": "engine.load", "fuel": "vehicle.fuel",
    ]

    public static func project(template: Data, draft: GaugeDraft, profileID: String,
                               baseRevision: UInt32, maxPages: Int = 8,
                               supportedLayouts: Set<GaugeLayout> = Set(GaugeLayout.allCases)) throws -> ConfigurationProjection {
        let blockers = draft.sendBlockers(maxPages: maxPages, supportedLayouts: supportedLayouts)
        guard blockers.isEmpty else { throw Failure.blocked(blockers) }
        guard let original = try? JSONSerialization.jsonObject(with: template) as? [String: Any],
              let available = original["definitions"] as? [[String: Any]],
              let id = original["vehicleProfileId"] as? String, !id.isEmpty,
              !profileID.isEmpty else { throw Failure.invalidTemplate }

        let required = Set((draft.pages.flatMap(\.pidIds) + draft.alerts.map(\.pidId))
            .compactMap { definitionIDs[$0] })
        let definitions = available.filter { ($0["id"] as? String).map(required.contains) ?? false }
        guard definitions.count == required.count else { throw Failure.invalidTemplate }

        let pages: [ProjectedPage] = draft.pages.map { page in
            ProjectedPage(id: page.id, name: page.name, renderer: page.layout.wireName,
                          pidIds: page.pidIds.map { definitionIDs[$0]! })
        }
        let alerts: [ProjectedAlert] = draft.alerts.map { alert in
            ProjectedAlert(id: alert.id, pidId: definitionIDs[alert.pidId]!,
                           direction: alert.direction.rawValue, warning: alert.warning,
                           critical: alert.critical)
        }
        var document = original
        document["baseRevision"] = Int(baseRevision)
        document["vehicleProfileId"] = profileID
        document["definitions"] = definitions
        document["pages"] = pages.map { page in
            ["id": page.id, "name": page.name, "renderer": page.renderer, "pidIds": page.pidIds] as [String: Any]
        }
        document["alerts"] = draft.alerts.map { alert in
            ["id": alert.id, "pidId": definitionIDs[alert.pidId]!,
             "direction": alert.direction.rawValue, "warning": alert.warning,
             "critical": alert.critical, "hysteresis": alert.hysteresis,
             "triggerDwellMs": alert.triggerDwellMs, "clearDwellMs": alert.clearDwellMs,
             "snoozeMs": 0, "priority": alert.priority] as [String: Any]
        }
        guard JSONSerialization.isValidJSONObject(document),
              let bytes = try? JSONSerialization.data(withJSONObject: document, options: [.sortedKeys]),
              bytes.count <= 65_536 else { throw Failure.invalidTemplate }
        return ConfigurationProjection(pages: pages, alerts: alerts, bytes: bytes)
    }
}
