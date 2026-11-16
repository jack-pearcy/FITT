// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "FittKit",
    // macOS is listed so `swift test` runs FittCore on the Mac without a simulator.
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "FittCore", targets: ["FittCore"]),
        .library(name: "FittUI", targets: ["FittUI"]),
    ],
    targets: [
        // Rules and API models. Foundation only: no UI, no networking, no storage.
        .target(name: "FittCore"),
        // The backend client, the app model, and the SwiftUI screens.
        .target(name: "FittUI", dependencies: ["FittCore"]),
        .testTarget(name: "FittCoreTests", dependencies: ["FittCore"]),
    ]
)
