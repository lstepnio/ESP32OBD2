import Foundation

/// The first owner read can return the legacy state or the active config status.
/// Either has already passed the firmware's protected GATT access control.
public enum ProtectedState: Equatable, Sendable {
    case builtIn(readingIndex: Int, rotation: Int, revision: UInt32)
    case configuration(phase: Int, activeRevision: UInt32, sha256: String)

    public enum DecodeError: Error { case malformed }

    public init(bytes: Data) throws {
        let bytes = Array(bytes)
        func u32(_ offset: Int) -> UInt32 {
            (0..<4).reduce(0) { $0 | UInt32(bytes[offset + $1]) << ($1 * 8) }
        }
        if bytes.count == 8 && bytes[0] == 2 {
            guard (0...4).contains(Int(bytes[1])), (0...4).contains(Int(bytes[2])),
                  (0...3).contains(Int(bytes[3])) else { throw DecodeError.malformed }
            self = .builtIn(readingIndex: Int(bytes[2]), rotation: Int(bytes[3]), revision: u32(4))
        } else if bytes.count == 64 && bytes[0] == 3 {
            guard bytes[1] <= 4, [0, 2, 3, 4, 5, 6].contains(bytes[2]) else {
                throw DecodeError.malformed
            }
            self = .configuration(phase: Int(bytes[1]), activeRevision: u32(20),
                sha256: bytes[32..<64].map { String(format: "%02x", $0) }.joined())
        } else {
            throw DecodeError.malformed
        }
    }
}
