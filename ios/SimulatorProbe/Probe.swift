import SwiftUI
import CoreBluetooth

// Development-only environment check. Never included in the companion product.
#if !targetEnvironment(simulator)
#error("The setup probe must only run in Simulator")
#endif

@MainActor
final class ProbeState: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published var bluetooth = "Checking"
    @Published var fixtureResult = "Checking"
    private var manager: CBCentralManager?
    private var fixtureCount = 0
    private var fixturePassed = false

    override init() {
        super.init()
        do {
            let url = Bundle.main.url(forResource: "runtime-identity", withExtension: "json")!
            let cases = try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as! [[String: Any]]
            for item in cases {
                let hex = Array(item["hex"] as! String)
                let data = Data(stride(from: 0, to: hex.count, by: 2).map {
                    UInt8(String(hex[$0...$0 + 1]), radix: 16)!
                })
                let identity = try? RuntimeIdentity(bytes: data)
                let accepted = item["accepted"] as! Bool
                guard accepted == (identity != nil) else { throw ProbeError.fixture }
                if let identity {
                    let result = confirmRuntime(
                        expectedRevision: (item["expectedRevision"] as! NSNumber).uint32Value,
                        expectedSha256: item["expectedSha256"] as! String, identity: identity)
                    guard result.rawValue == item["confirmation"] as! String,
                          identity.storedRevision == (item["storedRevision"] as! NSNumber).uint32Value
                    else { throw ProbeError.fixture }
                }
            }
            guard !cases.isEmpty else { throw ProbeError.fixture }
            fixtureCount = cases.count
            fixturePassed = true
            fixtureResult = "\(cases.count) shared cases passed"
        } catch {
            fixtureResult = "Failed: \(error)"
        }
        saveResult()
        manager = CBCentralManager(delegate: self, queue: .main,
            options: [CBCentralManagerOptionShowPowerAlertKey: false])
    }

    nonisolated func centralManagerDidUpdateState(_ central: CBCentralManager) {
        let state = central.state.rawValue
        Task { @MainActor in
            bluetooth = ["Unknown", "Resetting", "Unsupported", "Unauthorized", "Powered off", "Powered on"]
                .indices.contains(state) ? ["Unknown", "Resetting", "Unsupported", "Unauthorized", "Powered off", "Powered on"][state] : "Unknown"
            saveResult()
        }
    }

    private func saveResult() {
        let result: [String: Any] = [
            "environment": "iOS Simulator", "fixturesPassed": fixturePassed,
            "fixtureCount": fixtureCount, "bluetoothState": bluetooth,
            "hardwareQualified": false,
        ]
        do {
            let url = URL.documentsDirectory.appendingPathComponent("probe-result.json")
            try JSONSerialization.data(withJSONObject: result, options: [.prettyPrinted, .sortedKeys])
                .write(to: url, options: .atomic)
        } catch { fixtureResult = "Could not write setup result: \(error)" }
    }

    enum ProbeError: Error { case fixture }
}

@main
struct SimulatorProbe: App {
    @StateObject private var state = ProbeState()
    var body: some Scene {
        WindowGroup {
            NavigationStack {
                List {
                    Section("Development environment") {
                        Label("eGauge iOS setup", systemImage: "checkmark.circle")
                        Text(state.fixtureResult)
                        Text("Bluetooth: \(state.bluetooth)")
                    }
                    Section("Evidence boundary") {
                        Text("This is a setup check, not the companion app.")
                        Text("No gauge connected. Pairing, Wi-Fi joining and physical updates require an iPhone.")
                    }
                }.navigationTitle("Simulator ready")
            }
        }
    }
}
