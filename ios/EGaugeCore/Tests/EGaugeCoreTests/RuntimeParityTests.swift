import Foundation
import XCTest
@testable import EGaugeCore

final class RuntimeParityTests: XCTestCase {
    private struct Fixture: Decodable {
        let id: String
        let hex: String
        let accepted: Bool
        let expectedRevision: UInt32?
        let expectedSha256: String?
        let confirmation: String?
        let storedRevision: UInt32?
    }

    func testSharedRuntimeFixtures() throws {
        // Read the single repository fixture, never a platform-specific copy.
        var root = URL(fileURLWithPath: #filePath)
        for _ in 0..<5 { root.deleteLastPathComponent() }
        let url = root.appendingPathComponent("contracts/parity/runtime-identity.json")
        let fixtures = try JSONDecoder().decode([Fixture].self, from: Data(contentsOf: url))
        XCTAssertFalse(fixtures.isEmpty)
        for fixture in fixtures {
            let characters = Array(fixture.hex)
            let bytes = try stride(from: 0, to: characters.count, by: 2).map { index in
                try XCTUnwrap(UInt8(String(characters[index...index + 1]), radix: 16))
            }
            if !fixture.accepted {
                XCTAssertThrowsError(try RuntimeIdentity(bytes: Data(bytes)), fixture.id)
                continue
            }
            let identity = try RuntimeIdentity(bytes: Data(bytes))
            XCTAssertEqual(identity.storedRevision, fixture.storedRevision, fixture.id)
            XCTAssertEqual(confirmRuntime(
                expectedRevision: try XCTUnwrap(fixture.expectedRevision),
                expectedSha256: try XCTUnwrap(fixture.expectedSha256), identity: identity
            ).rawValue, fixture.confirmation, fixture.id)
        }
    }
}
