import Foundation
import XCTest
@testable import EGaugeCore

final class ProtectedStateTests: XCTestCase {
    func testBuiltInAndConfiguredOwnerRead() throws {
        XCTAssertEqual(try ProtectedState(bytes: Data([2, 1, 3, 2, 9, 0, 0, 0])),
                       .builtIn(readingIndex: 3, rotation: 2, revision: 9))
        var configured = Data(repeating: 0, count: 64)
        configured[0] = 3; configured[1] = 4; configured[20] = 7
        XCTAssertEqual(try ProtectedState(bytes: configured),
                       .configuration(phase: 4, activeRevision: 7, sha256: String(repeating: "00", count: 32)))
        XCTAssertThrowsError(try ProtectedState(bytes: Data(repeating: 0, count: 8)))
    }
}
