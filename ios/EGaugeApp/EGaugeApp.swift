import SwiftUI

@main
struct EGaugeApp: App {
    @StateObject private var store = AppStore()
    @StateObject private var connection = GaugeConnection()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(connection)
        }
    }
}
