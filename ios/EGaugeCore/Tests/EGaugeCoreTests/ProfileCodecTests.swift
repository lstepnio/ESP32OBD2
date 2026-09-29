import Foundation
import XCTest
@testable import EGaugeCore

final class ProfileCodecTests: XCTestCase {
    func testNewProfileMatchesAndroidDefaultPages() {
        let draft = VehicleProfile(id: "default", name: "My vehicle").draft
        XCTAssertEqual(draft.layout, .Numeric)
        XCTAssertEqual(draft.pages.map(\.id), ["page.1.rpm", "page.2.coolant", "page.3.speed"])
        XCTAssertEqual(draft.pages.map(\.name), ["ENGINE RPM", "COOLANT", "SPEED"])
        XCTAssertEqual(draft.pages.map(\.layout), [.Numeric, .Numeric, .Numeric])
        XCTAssertEqual(draft.alerts.first?.pidId, "coolant")
    }

    func testLegacyAlertsMigrateWithoutChangingLimits() throws {
        let legacy = """
        {"schemaVersion":2,"activeId":"jeep","profiles":[{"id":"jeep","name":"Jeep",
         "secondAdapterEnabled":true,"draft":{"pidId":"rpm","layout":"Arc","source":"ECM",
         "warning":105,"critical":115,"hysteresis":3,"triggerDwellMs":1000,"clearDwellMs":2000}}]}
        """.data(using: .utf8)!
        let migrated = try ProfileCodec.decode(legacy)
        XCTAssertEqual(migrated.activeId, "jeep")
        XCTAssertTrue(migrated.profiles[0].secondAdapterEnabled)
        XCTAssertEqual(migrated.profiles[0].draft.pages.count, 3)
        XCTAssertEqual(migrated.profiles[0].draft.alerts[0].warning, 105)
        XCTAssertEqual(migrated.profiles[0].draft.alerts[0].critical, 115)
        XCTAssertEqual(try ProfileCodec.decode(ProfileCodec.encode(migrated)), migrated)
    }

    func testDirectionAndDisabledDraftSurviveRoundTrip() throws {
        var draft = GaugeDraft()
        draft.alerts = [GaugeAlert(id: "alert.coolant", pidId: "coolant", direction: .below,
                                   warning: 50, critical: 30)]
        draft.source = "TCM"
        let value = ProfileCollection(profiles: [VehicleProfile(id: "default", name: "My vehicle", draft: draft)])
        let restored = try ProfileCodec.decode(ProfileCodec.encode(value))
        XCTAssertEqual(restored, value)
        XCTAssertTrue(restored.profiles[0].draft.sendBlockers().contains(where: { $0.contains("main adapter") }))
    }

    func testInvalidPageDoesNotBecomeSendable() {
        var draft = GaugeDraft()
        draft.pages[0].layout = .Dual
        XCTAssertTrue(draft.sendBlockers().contains(where: { $0.contains("Page 1") }))
        draft.pages[0].layout = .Numeric
        draft.pages[0].name = "   "
        XCTAssertTrue(draft.sendBlockers().contains(where: { $0.contains("name") }))
    }
}
