import EGaugeCore
import SwiftUI

private enum MainTab: Hashable { case gauge, car, settings, expert }

struct RootView: View {
    @EnvironmentObject private var store: AppStore
    @State private var tab: MainTab = .gauge

    var body: some View {
        TabView(selection: $tab) {
            NavigationStack { GaugeHomeView() }
                .tabItem { Label("Gauge", systemImage: "gauge.with.dots.needle.67percent") }.tag(MainTab.gauge)
            NavigationStack { CarView() }
                .tabItem { Label("Car", systemImage: "car.side") }.tag(MainTab.car)
            NavigationStack { SettingsView() }
                .tabItem { Label("Settings", systemImage: "gearshape") }.tag(MainTab.settings)
            if store.advancedTools {
                NavigationStack { ExpertView() }
                    .tabItem { Label("Expert", systemImage: "wrench.adjustable") }.tag(MainTab.expert)
            }
        }
        .onChange(of: store.advancedTools) { _, enabled in if !enabled && tab == .expert { tab = .settings } }
        .gaugePage()
    }
}

struct GaugeHomeView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var connection: GaugeConnection
    @State private var selectedPage = 0

    private var pages: [GaugePage] { store.active.draft.pages }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                HStack {
                    Text(store.gaugeName).font(.largeTitle.bold()).minimumScaleFactor(0.8)
                    Spacer()
                    Text(connection.ownerAuthenticated ? "Gauge ready" : "Preview")
                        .font(.caption.bold()).padding(.horizontal, 11).padding(.vertical, 7)
                        .background(GaugeTheme.raised, in: Capsule())
                }
                if !pages.isEmpty {
                    TabView(selection: $selectedPage) {
                        ForEach(pages.indices, id: \.self) { index in
                            GaugePreview(page: pages[index])
                                .frame(maxWidth: 300).padding(.horizontal, 24)
                                .tag(index)
                        }
                    }
                    .frame(height: 310)
                    .tabViewStyle(.page(indexDisplayMode: .always))
                    Text("Swipe between pages. Values are examples.")
                        .font(.footnote).foregroundStyle(GaugeTheme.muted)
                }
                GaugeCard {
                    VStack(alignment: .leading, spacing: 7) {
                        Text(connection.ownerAuthenticated ? "Your gauge is connected" : "Your gauge has not been checked")
                            .font(.headline)
                        Text(connection.status).font(.subheadline).foregroundStyle(GaugeTheme.muted)
                    }
                }
                if let error = store.storageError {
                    GaugeCard { Label(error, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(GaugeTheme.critical) }
                }
                NavigationLink {
                    CustomizeView(initialPage: min(selectedPage, max(0, pages.count - 1)))
                } label: {
                    Text("Customize gauge").font(.headline).frame(maxWidth: .infinity).padding(16)
                }
                .buttonStyle(.plain)
                .foregroundStyle(GaugeTheme.canvas)
                .background(GaugeTheme.accent, in: RoundedRectangle(cornerRadius: 14))
                .disabled(!store.canEdit)
                NavigationLink("Set up gauge") { SetupView() }
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
            .padding(20)
            .frame(maxWidth: 760)
            .frame(maxWidth: .infinity)
        }
        .navigationTitle("Gauge")
        .navigationBarTitleDisplayMode(.inline)
        .gaugePage()
    }
}

struct CarView: View {
    @EnvironmentObject private var store: AppStore
    @State private var showingAdd = false
    @State private var newName = ""

    var body: some View {
        List {
            Section("Vehicles") {
                ForEach(store.collection.profiles) { profile in
                    Button {
                        store.selectProfile(profile.id)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(profile.name).foregroundStyle(GaugeTheme.text)
                                Text("\(profile.draft.pages.count) pages · \(profile.draft.alerts.count) alerts")
                                    .font(.caption).foregroundStyle(GaugeTheme.muted)
                            }
                            Spacer()
                            if store.collection.activeId == profile.id {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(GaugeTheme.accent)
                            }
                        }
                    }.disabled(!store.canEdit)
                }
                if store.collection.profiles.count < 8 {
                    Button("Add vehicle") { showingAdd = true }.disabled(!store.canEdit)
                }
            }
            Section("Vehicle checks") {
                Text("Live readings and fault checks will appear after an adapter connection is implemented and tested.")
                    .foregroundStyle(GaugeTheme.muted)
                Text("Example readings never confirm vehicle support.")
                    .font(.footnote).foregroundStyle(GaugeTheme.muted)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Car")
        .gaugePage()
        .alert("Add vehicle", isPresented: $showingAdd) {
            TextField("Vehicle name", text: $newName)
            Button("Add") { store.addProfile(name: newName); newName = "" }
                .disabled(!store.canAddProfile(named: newName))
            Button("Cancel", role: .cancel) { newName = "" }
        } message: {
            Text(store.collection.profiles.contains {
                $0.name.localizedCaseInsensitiveCompare(newName.trimmingCharacters(in: .whitespacesAndNewlines)) == .orderedSame
            } ? "This name is already used. Choose a different name." :
                "Choose a unique name of up to 32 characters. This keeps a separate draft on this phone.")
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var store: AppStore
    @EnvironmentObject private var connection: GaugeConnection
    var body: some View {
        Form {
            Section("Gauge") {
                TextField("Local gauge name", text: $store.gaugeName)
                    .textInputAutocapitalization(.words)
                NavigationLink("Connection and pairing") { SetupView() }
                Text(connection.ownerAuthenticated ? "Owner access confirmed" : "No owner connection confirmed")
                    .foregroundStyle(GaugeTheme.muted)
            }
            Section("Updates") {
                Text("Firmware installation is not available in this iOS build.")
                    .foregroundStyle(GaugeTheme.muted)
                Text("A signed update must be verified, sent, and confirmed running before this can be enabled.")
                    .font(.footnote).foregroundStyle(GaugeTheme.muted)
            }
            Section("Preferences") {
                Toggle("Show advanced tools", isOn: $store.advancedTools)
            }
            Section("About") {
                Text("eGauge iOS 0.1")
                Text("Offline preview and local editing. Gauge controls are still in development.")
                    .foregroundStyle(GaugeTheme.muted)
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Settings")
        .gaugePage()
    }
}

struct SetupView: View {
    @EnvironmentObject private var connection: GaugeConnection

    var body: some View {
        List {
            Section("Find your gauge") {
                Text(connection.status)
                Button(connection.scanning ? "Stop search" : "Find nearby gauges") {
                    if connection.scanning { connection.cancelScan() }
                    else { connection.findNearby() }
                }
                ForEach(connection.candidates) { candidate in
                    Button {
                        connection.choose(candidate.id)
                    } label: {
                        HStack {
                            Text(candidate.name)
                            Spacer()
                            Text("\(candidate.signal) dBm").font(.caption).foregroundStyle(GaugeTheme.muted)
                        }
                    }
                }
            }
            Section("Owner access") {
                Text("Use the code shown on the physical gauge if iOS asks to pair. The app confirms owner access only after a protected read succeeds.")
                    .foregroundStyle(GaugeTheme.muted)
                if let board = connection.boardName { Text("Board: \(board)") }
                if connection.ownerAuthenticated { Label("Owner access confirmed", systemImage: "checkmark.shield") }
            }
        }
        .scrollContentBackground(.hidden)
        .navigationTitle("Set up gauge")
        .onDisappear { connection.cancelScan() }
        .gaugePage()
    }
}

struct ExpertView: View {
    @State private var query = ""
    var body: some View {
        List {
            Section("Example readings") {
                ForEach(ReadingDefinition.executable.filter {
                    query.isEmpty || $0.name.localizedCaseInsensitiveContains(query)
                }) { reading in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(reading.name)
                        Text("\(reading.id) · \(reading.unit) · Example only")
                            .font(.caption).foregroundStyle(GaugeTheme.muted)
                    }
                }
            }
            Section("Technical boundary") {
                Text("No OBD request is sent from this screen. Adapter discovery and code clearing are unavailable.")
                    .foregroundStyle(GaugeTheme.muted)
            }
        }
        .searchable(text: $query, prompt: "Search example readings")
        .scrollContentBackground(.hidden)
        .navigationTitle("Expert")
        .gaugePage()
    }
}
