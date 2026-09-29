import Combine
import CoreBluetooth
import EGaugeCore
import Foundation

struct NearbyGauge: Identifiable {
    let id: UUID
    let name: String
    let signal: Int
}

/// Read-only connection path. Protected state read is the ownership proof.
/// Configuration and OTA controls remain unavailable until verified on an iPhone.
final class GaugeConnection: NSObject, ObservableObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    @Published private(set) var status = "Gauge not checked"
    @Published private(set) var candidates: [NearbyGauge] = []
    @Published private(set) var scanning = false
    @Published private(set) var ownerAuthenticated = false
    @Published private(set) var boardName: String?

    private let serviceID = CBUUID(string: "6f1a0000-9e3b-4f45-a714-69c9d23b6c00")
    private let capabilityID = CBUUID(string: "6f1a0001-9e3b-4f45-a714-69c9d23b6c00")
    private let stateID = CBUUID(string: "6f1a0003-9e3b-4f45-a714-69c9d23b6c00")
    private var central: CBCentralManager?
    private var discovered: [UUID: CBPeripheral] = [:]
    private var selected: CBPeripheral?
    private var pendingScan = false
    private var scanGeneration = 0
    private var connectionGeneration = 0

    func findNearby() {
        cancelScan()
        selected.map { central?.cancelPeripheralConnection($0) }
        connectionGeneration += 1
        selected = nil
        ownerAuthenticated = false
        boardName = nil
        candidates = []
        discovered = [:]
        pendingScan = true
        status = "Checking Bluetooth"
        if central == nil { central = CBCentralManager(delegate: self, queue: .main) }
        else { startIfReady() }
    }

    func cancelScan() {
        scanGeneration += 1
        central?.stopScan()
        if scanning { status = candidates.isEmpty ? "Search stopped" : "Choose your gauge" }
        scanning = false
        pendingScan = false
    }

    func choose(_ id: UUID) {
        guard let peripheral = discovered[id] else { return }
        cancelScan()
        selected = peripheral
        peripheral.delegate = self
        status = "Connecting to \(peripheral.name ?? "gauge")"
        central?.connect(peripheral)
        expectReply(from: peripheral)
    }

    private func expectReply(from peripheral: CBPeripheral) {
        connectionGeneration += 1
        let generation = connectionGeneration
        DispatchQueue.main.asyncAfter(deadline: .now() + 12) { [weak self] in
            guard let self, self.connectionGeneration == generation,
                  self.selected?.identifier == peripheral.identifier,
                  !self.ownerAuthenticated else { return }
            self.selected = nil
            self.ownerAuthenticated = false
            self.central?.cancelPeripheralConnection(peripheral)
            self.status = "Gauge check timed out. Try again."
        }
    }

    private func failCheck(_ message: String) {
        connectionGeneration += 1
        ownerAuthenticated = false
        if let selected {
            central?.cancelPeripheralConnection(selected)
            self.selected = nil
        }
        status = message
    }

    private func startIfReady() {
        guard pendingScan, let central else { return }
        switch central.state {
        case .poweredOn:
            pendingScan = false
            scanning = true
            status = "Finding nearby gauges"
            let generation = scanGeneration
            central.scanForPeripherals(withServices: [serviceID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
            DispatchQueue.main.asyncAfter(deadline: .now() + 8) { [weak self] in
                guard let self, self.scanGeneration == generation else { return }
                self.cancelScan()
                self.status = self.candidates.isEmpty ? "No nearby gauge found" : "Choose your gauge"
            }
        case .poweredOff: status = "Turn on Bluetooth to find your gauge"
        case .unauthorized: status = "Allow Bluetooth access in Settings to find your gauge"
        case .unsupported: status = "Bluetooth is unavailable on this device"
        default: status = "Waiting for Bluetooth"
        }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn {
            ownerAuthenticated = false
            connectionGeneration += 1
            if let selected { central.cancelPeripheralConnection(selected); self.selected = nil }
            if scanning { cancelScan() }
        }
        if pendingScan { startIfReady() }
        else {
            switch central.state {
            case .poweredOff: status = "Bluetooth is off"
            case .unauthorized: status = "Bluetooth access is not allowed"
            case .unsupported: status = "Bluetooth is unavailable on this device"
            default: break
            }
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard discovered[peripheral.identifier] == nil else { return }
        discovered[peripheral.identifier] = peripheral
        let name = advertisementData[CBAdvertisementDataLocalNameKey] as? String
            ?? peripheral.name ?? "eGauge"
        candidates.append(NearbyGauge(id: peripheral.identifier, name: name, signal: RSSI.intValue))
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard selected?.identifier == peripheral.identifier else { return }
        status = "Reading gauge information"
        peripheral.discoverServices([serviceID])
        expectReply(from: peripheral)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        guard selected?.identifier == peripheral.identifier else { return }
        connectionGeneration += 1
        ownerAuthenticated = false
        status = "Could not connect to the gauge. Try again."
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral,
                        error: Error?) {
        guard selected?.identifier == peripheral.identifier else { return }
        connectionGeneration += 1
        ownerAuthenticated = false
        status = "Gauge disconnected. Reconnect to check its settings."
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard selected?.identifier == peripheral.identifier else { return }
        guard error == nil, let service = peripheral.services?.first(where: { $0.uuid == serviceID }) else {
            failCheck("This gauge does not offer the companion service")
            return
        }
        peripheral.discoverCharacteristics([capabilityID, stateID], for: service)
        expectReply(from: peripheral)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard selected?.identifier == peripheral.identifier else { return }
        guard error == nil,
              let capability = service.characteristics?.first(where: { $0.uuid == capabilityID }) else {
            failCheck("Gauge capabilities are unavailable")
            return
        }
        peripheral.readValue(for: capability)
        expectReply(from: peripheral)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        guard selected?.identifier == peripheral.identifier else { return }
        guard error == nil, let bytes = characteristic.value else {
            failCheck(characteristic.uuid == stateID
                ? "Protected read failed. Check the code on the gauge and iOS pairing prompt."
                : "Could not read gauge capabilities")
            return
        }
        if characteristic.uuid == capabilityID {
            guard let json = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
                  json["protocolMajor"] as? Int == 0,
                  json["configWrite"] as? Bool == false,
                  json["ota"] as? Bool == false,
                  let board = json["board"] as? String else {
                failCheck("Gauge protocol is unavailable or unsupported")
                return
            }
            boardName = board
            status = "Checking owner access"
            guard let state = peripheral.services?.first(where: { $0.uuid == serviceID })?
                .characteristics?.first(where: { $0.uuid == stateID }) else {
                failCheck("This gauge does not offer protected state")
                return
            }
            peripheral.readValue(for: state)
            expectReply(from: peripheral)
        } else if characteristic.uuid == stateID {
            guard (try? ProtectedState(bytes: bytes)) != nil else {
                failCheck("Protected gauge state is invalid")
                return
            }
            ownerAuthenticated = true
            connectionGeneration += 1
            status = "Gauge ready. Owner access confirmed."
            UserDefaults.standard.set(peripheral.identifier.uuidString, forKey: "remembered-gauge-id")
        }
    }
}
