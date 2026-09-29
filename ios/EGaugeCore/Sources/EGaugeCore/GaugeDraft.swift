import Foundation

public struct ReadingDefinition: Identifiable, Equatable, Sendable {
    public let id: String
    public let name: String
    public let gaugeLabel: String
    public let unit: String
    public let example: String
    public let range: ClosedRange<Int>

    public static let executable: [ReadingDefinition] = [
        .init(id: "rpm", name: "Engine RPM", gaugeLabel: "ENGINE RPM", unit: "rpm", example: "2,840", range: 0...16_383),
        .init(id: "coolant", name: "Coolant temperature", gaugeLabel: "COOLANT", unit: "°C", example: "92", range: -40...215),
        .init(id: "speed", name: "Vehicle speed", gaugeLabel: "SPEED", unit: "km/h", example: "64", range: 0...255),
        .init(id: "load", name: "Calculated load", gaugeLabel: "ENGINE LOAD", unit: "%", example: "38", range: 0...100),
        .init(id: "fuel", name: "Fuel level", gaugeLabel: "FUEL LEVEL", unit: "%", example: "73", range: 0...100),
    ]

    public static func find(_ id: String) -> ReadingDefinition? { executable.first { $0.id == id } }
}

public enum GaugeLayout: String, Codable, CaseIterable, Sendable {
    case Numeric, Arc, Bar, Trend, Dual
    public var label: String { rawValue }
    public var wireName: String { rawValue.lowercased() }
}

public struct GaugePage: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var layout: GaugeLayout
    public var pidIds: [String]

    public init(id: String, name: String, layout: GaugeLayout, pidIds: [String]) {
        self.id = id; self.name = name; self.layout = layout; self.pidIds = pidIds
    }
}

public enum AlertDirection: String, Codable, CaseIterable, Sendable {
    case above, below
    public var label: String { self == .above ? "Rises above" : "Falls below" }
}

public struct GaugeAlert: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var pidId: String
    public var direction: AlertDirection
    public var warning: Int
    public var critical: Int
    public var hysteresis: Int
    public var triggerDwellMs: Int
    public var clearDwellMs: Int
    public var priority: Int

    public init(id: String, pidId: String, direction: AlertDirection = .above,
                warning: Int, critical: Int, hysteresis: Int = 3,
                triggerDwellMs: Int = 1_000, clearDwellMs: Int = 2_000, priority: Int = 8) {
        self.id = id; self.pidId = pidId; self.direction = direction
        self.warning = warning; self.critical = critical; self.hysteresis = hysteresis
        self.triggerDwellMs = triggerDwellMs; self.clearDwellMs = clearDwellMs
        self.priority = priority
    }

    public static func initial(for id: String) -> GaugeAlert? {
        guard let reading = ReadingDefinition.find(id) else { return nil }
        let limits: (Int, Int) = switch id {
        case "coolant": (105, 115)
        case "rpm": (4_000, 5_000)
        case "speed": (120, 140)
        default: (80, 90)
        }
        return .init(id: "alert.\(id)", pidId: id, warning: limits.0, critical: limits.1,
                     hysteresis: min(20, max(1, (reading.range.upperBound - reading.range.lowerBound) / 50)))
    }
}

public struct GaugeDraft: Codable, Equatable, Sendable {
    public var pidId: String
    public var layout: GaugeLayout
    public var source: String
    public var pages: [GaugePage]
    public var alerts: [GaugeAlert]

    public init(pidId: String = "rpm", layout: GaugeLayout = .Numeric, source: String = "ECM",
                pages: [GaugePage]? = nil, alerts: [GaugeAlert]? = nil) {
        self.pidId = pidId; self.layout = layout; self.source = source
        self.pages = pages ?? GaugeDraft.defaultPages(primary: pidId, layout: layout)
        self.alerts = alerts ?? [GaugeAlert.initial(for: "coolant")!]
    }

    public static func defaultPages(primary: String = "rpm", layout: GaugeLayout = .Numeric) -> [GaugePage] {
        var ordered: [String] = []
        for id in [primary, "rpm", "coolant", "speed"] where !ordered.contains(id) {
            ordered.append(id)
        }
        return ordered.prefix(3).enumerated().map { index, id in
            GaugePage(id: "page.\(index + 1).\(id)", name: ReadingDefinition.find(id)?.gaugeLabel ?? id,
                      layout: index == 0 ? layout : .Numeric, pidIds: [id])
        }
    }

    /// Mirrors the newer Android projector. Stored drafts may be readable but unsendable.
    public func sendBlockers(maxPages: Int = 8, supportedLayouts: Set<GaugeLayout> = Set(GaugeLayout.allCases)) -> [String] {
        var errors: [String] = []
        if source != "ECM" { errors.append("Only readings from the main adapter can be sent.") }
        if !(1...min(8, maxPages)).contains(pages.count) { errors.append("Choose between one and \(min(8, maxPages)) pages.") }
        if Set(pages.map(\.id)).count != pages.count { errors.append("Every page needs a unique identity.") }
        for (index, page) in pages.enumerated() {
            if page.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || page.name.count > 32 {
                errors.append("Page \(index + 1) needs a name of at most 32 characters.")
            }
            if !supportedLayouts.contains(page.layout) { errors.append("\(page.layout.label) is preview only on this gauge.") }
            if page.pidIds.count != (page.layout == .Dual ? 2 : 1) ||
                Set(page.pidIds).count != page.pidIds.count ||
                page.pidIds.contains(where: { ReadingDefinition.find($0) == nil }) {
                errors.append("Page \(index + 1) needs supported, distinct readings.")
            }
        }
        if alerts.count > 32 { errors.append("Choose up to 32 alerts.") }
        if Set(alerts.map(\.id)).count != alerts.count || Set(alerts.map(\.pidId)).count != alerts.count {
            errors.append("Each alert needs a unique reading and identity.")
        }
        for alert in alerts {
            guard let reading = ReadingDefinition.find(alert.pidId) else {
                errors.append("An alert uses an unavailable reading."); continue
            }
            if !reading.range.contains(alert.warning) || !reading.range.contains(alert.critical) {
                errors.append("\(reading.name) alert limits are outside its supported range.")
            }
            let ordered = alert.direction == .above ? alert.warning < alert.critical : alert.warning > alert.critical
            if !ordered { errors.append("\(reading.name) critical limit must be beyond the warning limit.") }
            if !(0...20).contains(alert.hysteresis) || alert.hysteresis >= reading.range.upperBound - reading.range.lowerBound {
                errors.append("\(reading.name) reset distance is outside its supported range.")
            }
            if alert.direction == .above ? alert.warning + alert.hysteresis >= alert.critical :
                alert.warning - alert.hysteresis <= alert.critical {
                errors.append("\(reading.name) needs more space between warning and critical.")
            }
            if !(0...60_000).contains(alert.triggerDwellMs) || !(0...60_000).contains(alert.clearDwellMs) {
                errors.append("\(reading.name) alert delay must be 60 seconds or less.")
            }
        }
        return errors
    }
}

public struct VehicleProfile: Codable, Identifiable, Equatable, Sendable {
    public var id: String
    public var name: String
    public var draft: GaugeDraft
    public var secondAdapterEnabled: Bool

    public init(id: String, name: String, draft: GaugeDraft = GaugeDraft(), secondAdapterEnabled: Bool = false) {
        self.id = id; self.name = name; self.draft = draft; self.secondAdapterEnabled = secondAdapterEnabled
    }
}

public struct ProfileCollection: Codable, Equatable, Sendable {
    public var schemaVersion: Int
    public var activeId: String
    public var profiles: [VehicleProfile]

    public init(activeId: String = "default", profiles: [VehicleProfile] = [VehicleProfile(id: "default", name: "My vehicle")]) {
        schemaVersion = 4; self.activeId = activeId; self.profiles = profiles
    }

    public var activeIndex: Int { profiles.firstIndex { $0.id == activeId } ?? 0 }
}
