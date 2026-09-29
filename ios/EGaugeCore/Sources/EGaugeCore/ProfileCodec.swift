import Foundation

/// Reads Android's versioned local profile format and writes the version 4 shape.
/// Imported drafts remain local. Validation for sending is a separate decision.
public enum ProfileCodec {
    public enum Failure: Error, Equatable { case invalid(String) }

    public static func decode(_ data: Data) throws -> ProfileCollection {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let version = root["schemaVersion"] as? Int, (1...4).contains(version),
              let items = root["profiles"] as? [[String: Any]], (1...8).contains(items.count),
              let activeId = root["activeId"] as? String else {
            throw Failure.invalid("Profile format is invalid or newer than this app")
        }
        var profiles: [VehicleProfile] = []
        for item in items {
            guard let id = item["id"] as? String, !id.isEmpty,
                  let name = item["name"] as? String, (1...32).contains(name.trimmingCharacters(in: .whitespaces).count),
                  let raw = item["draft"] as? [String: Any],
                  let primary = raw["pidId"] as? String, knownReading(primary),
                  let layoutName = raw["layout"] as? String, let layout = GaugeLayout(rawValue: layoutName),
                  let source = raw["source"] as? String, ["ECM", "TCM"].contains(source) else {
                throw Failure.invalid("A profile contains an unsupported identity or draft")
            }
            let pages: [GaugePage]
            if version >= 3 {
                guard let rawPages = raw["pages"] as? [[String: Any]], (1...8).contains(rawPages.count) else {
                    throw Failure.invalid("Profile page count is invalid")
                }
                pages = try rawPages.map { page in
                    guard let pageId = page["id"] as? String, validId(pageId),
                          let pageName = page["name"] as? String, (1...32).contains(pageName.count),
                          let layoutName = page["layout"] as? String,
                          let pageLayout = GaugeLayout(rawValue: layoutName),
                          let ids = page["pidIds"] as? [String],
                          ids.count == (pageLayout == .Dual ? 2 : 1),
                          Set(ids).count == ids.count, ids.allSatisfy(knownReading) else {
                        throw Failure.invalid("Profile page identity or reading is invalid")
                    }
                    return GaugePage(id: pageId, name: pageName, layout: pageLayout, pidIds: ids)
                }
            } else {
                pages = GaugeDraft.defaultPages(primary: primary, layout: layout)
            }
            if Set(pages.map(\.id)).count != pages.count { throw Failure.invalid("Profile page IDs are duplicated") }
            let alerts: [GaugeAlert]
            if version >= 4 {
                guard let rawAlerts = raw["alerts"] as? [[String: Any]], rawAlerts.count <= 32 else {
                    throw Failure.invalid("Profile alert count is invalid")
                }
                alerts = try rawAlerts.map { alert in
                    guard let alertId = alert["id"] as? String, validId(alertId),
                          let readingId = alert["pidId"] as? String, knownReading(readingId),
                          let direction = AlertDirection(rawValue: alert["direction"] as? String ?? "above"),
                          let warning = alert["warning"] as? Int,
                          let critical = alert["critical"] as? Int else {
                        throw Failure.invalid("Profile alert identity or limits are invalid")
                    }
                    return GaugeAlert(id: alertId, pidId: readingId, direction: direction,
                        warning: warning, critical: critical,
                        hysteresis: alert["hysteresis"] as? Int ?? 3,
                        triggerDwellMs: alert["triggerDwellMs"] as? Int ?? 1_000,
                        clearDwellMs: alert["clearDwellMs"] as? Int ?? 2_000,
                        priority: alert["priority"] as? Int ?? 8)
                }
            } else {
                guard let warning = raw["warning"] as? Int, let critical = raw["critical"] as? Int else {
                    throw Failure.invalid("Legacy alert limits are missing")
                }
                alerts = [GaugeAlert(id: "alert.coolant", pidId: "coolant",
                    warning: warning, critical: critical,
                    hysteresis: raw["hysteresis"] as? Int ?? 3,
                    triggerDwellMs: raw["triggerDwellMs"] as? Int ?? 1_000,
                    clearDwellMs: raw["clearDwellMs"] as? Int ?? 2_000)]
            }
            if Set(alerts.map(\.id)).count != alerts.count || alerts.contains(where: { alert in
                let range = alert.pidId == "rpm" ? 0...16_384 : ReadingDefinition.find(alert.pidId)?.range ?? 0...100
                return !range.contains(alert.warning) || !range.contains(alert.critical) ||
                    !(0...20).contains(alert.hysteresis) || !(0...60_000).contains(alert.triggerDwellMs) ||
                    !(0...60_000).contains(alert.clearDwellMs)
            }) { throw Failure.invalid("Profile alert settings are invalid") }
            let second = version >= 2 ? item["secondAdapterEnabled"] as? Bool ?? false : source == "TCM"
            profiles.append(VehicleProfile(id: id, name: name.trimmingCharacters(in: .whitespaces),
                draft: GaugeDraft(pidId: primary, layout: layout, source: source,
                                  pages: pages, alerts: alerts), secondAdapterEnabled: second))
        }
        guard Set(profiles.map(\.id)).count == profiles.count,
              profiles.contains(where: { $0.id == activeId }) else {
            throw Failure.invalid("Profile identity or selection is invalid")
        }
        return ProfileCollection(activeId: activeId, profiles: profiles)
    }

    public static func encode(_ value: ProfileCollection) throws -> Data {
        guard (1...8).contains(value.profiles.count),
              value.profiles.contains(where: { $0.id == value.activeId }) else {
            throw Failure.invalid("Profile selection is invalid")
        }
        var current = value
        current.schemaVersion = 4
        return try JSONEncoder().encode(current)
    }

    private static func knownReading(_ id: String) -> Bool {
        ReadingDefinition.find(id) != nil || id == "tcm"
    }

    private static func validId(_ id: String) -> Bool {
        id.range(of: "^[a-z][a-z0-9._-]{0,63}$", options: .regularExpression) != nil
    }
}
