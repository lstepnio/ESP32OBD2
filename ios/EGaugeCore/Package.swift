// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "EGaugeCore",
    platforms: [.iOS(.v18), .macOS(.v15)],
    products: [.library(name: "EGaugeCore", targets: ["EGaugeCore"])],
    targets: [
        .target(name: "EGaugeCore"),
        .testTarget(name: "EGaugeCoreTests", dependencies: ["EGaugeCore"]),
    ]
)
