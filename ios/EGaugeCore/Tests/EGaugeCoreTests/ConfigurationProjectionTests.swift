import Foundation
import XCTest
@testable import EGaugeCore

final class ConfigurationProjectionTests: XCTestCase {
    private func template() throws -> Data {
        var root = URL(fileURLWithPath: #filePath)
        for _ in 0..<5 { root.deleteLastPathComponent() }
        return try Data(contentsOf: root.appendingPathComponent("android/app/src/main/assets/numeric_config_template.json"))
    }

    func testReviewAndPayloadCarryEveryPageAndReadingSpecificAlert() throws {
        var draft = GaugeDraft()
        draft.pages = [GaugePage(id: "page.speed", name: "Speed", layout: .Trend, pidIds: ["speed"]),
                       GaugePage(id: "page.dual", name: "Powertrain", layout: .Dual, pidIds: ["rpm", "coolant"])]
        draft.alerts = [GaugeAlert(id: "alert.coolant", pidId: "coolant", warning: 105, critical: 115),
                        GaugeAlert(id: "alert.speed", pidId: "speed", direction: .below,
                                   warning: 30, critical: 10)]
        let projection = try ConfigurationProjector.project(template: template(), draft: draft,
                                                            profileID: "vehicle.example", baseRevision: 8)
        XCTAssertEqual(projection.pages.map(\.renderer), ["trend", "dual"])
        XCTAssertEqual(projection.pages[1].pidIds, ["engine.rpm", "engine.coolant"])
        XCTAssertEqual(projection.alerts.map(\.pidId), ["engine.coolant", "vehicle.speed"])
        let wire = try XCTUnwrap(JSONSerialization.jsonObject(with: projection.bytes) as? [String: Any])
        XCTAssertEqual(wire["baseRevision"] as? Int, 8)
        XCTAssertEqual(wire["vehicleProfileId"] as? String, "vehicle.example")
        XCTAssertEqual((wire["pages"] as? [[String: Any]])?.count, 2)
        XCTAssertEqual((wire["alerts"] as? [[String: Any]])?.count, 2)
        XCTAssertEqual((wire["definitions"] as? [[String: Any]])?.count, 3)
    }

    func testUnsupportedSourceAndBadAlertNeverProducePayload() throws {
        var draft = GaugeDraft()
        draft.source = "TCM"
        draft.alerts[0].critical = 80
        XCTAssertThrowsError(try ConfigurationProjector.project(template: template(), draft: draft,
                                                                profileID: "vehicle.example", baseRevision: 8))
    }
}
