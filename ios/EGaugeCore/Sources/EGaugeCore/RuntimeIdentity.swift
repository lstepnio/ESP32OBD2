import Foundation

/// Protocol-0 protected runtime snapshot. Receiving bytes alone does not prove ownership.
public struct RuntimeIdentity: Equatable, Sendable {
    public let running: Bool
    public let usedPreviousGeneration: Bool
    public let trial: Bool
    public let revision: UInt32
    public let storedRevision: UInt32
    public let sha256: String

    public enum DecodeError: Error { case malformed }

    public init(bytes: Data) throws {
        let bytes = Array(bytes)
        guard bytes.count == 44, bytes[0] == 8, bytes[2] == 0, bytes[3] == 0,
              bytes[1] & 0xf8 == 0 else { throw DecodeError.malformed }
        func u32(_ offset: Int) -> UInt32 {
            (0..<4).reduce(0) { $0 | UInt32(bytes[offset + $1]) << ($1 * 8) }
        }
        running = bytes[1] & 1 != 0
        usedPreviousGeneration = bytes[1] & 2 != 0
        trial = bytes[1] & 4 != 0
        revision = u32(4)
        storedRevision = u32(8)
        let digest = bytes[12..<44]
        guard (running && revision > 0 && storedRevision >= revision) ||
              (!running && revision == 0 && digest.allSatisfy { $0 == 0 }) else {
            throw DecodeError.malformed
        }
        sha256 = digest.map { String(format: "%02x", $0) }.joined()
    }
}

public enum RuntimeConfirmation: String, Sendable {
    case waiting = "WAITING", active = "ACTIVE", recovered = "RECOVERED", rejected = "REJECTED"
}

public func confirmRuntime(expectedRevision: UInt32, expectedSha256: String,
                           identity: RuntimeIdentity) -> RuntimeConfirmation {
    if identity.usedPreviousGeneration { return .recovered }
    guard identity.running, identity.revision == expectedRevision,
          identity.sha256 == expectedSha256 else { return .rejected }
    return identity.trial ? .waiting : .active
}
