// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "FittKit",
    // macOS is listed so `swift test` runs FittCore on the Mac without a simulator.
    // watchOS is listed for the watch app, which uses FittCore and FittAPI and
    // none of the phone's screens.
    platforms: [.iOS(.v17), .macOS(.v14), .watchOS(.v10)],
    products: [
        .library(name: "FittCore", targets: ["FittCore"]),
        .library(name: "FittAPI", targets: ["FittAPI"]),
        .library(name: "FittUI", targets: ["FittUI"]),
    ],
    targets: [
        // Rules and API models. Foundation only: no UI, no networking, no storage.
        .target(name: "FittCore"),
        // The backend client and the token storage. No UI, so the phone and the
        // watch share one copy of how the server is spoken to.
        .target(name: "FittAPI", dependencies: ["FittCore"]),
        // The phone's app model and SwiftUI screens.
        .target(name: "FittUI", dependencies: ["FittCore", "FittAPI"]),
        .testTarget(name: "FittCoreTests", dependencies: ["FittCore"]),
        // Backend and AppModel against a stubbed URLSession. No network, no Keychain.
        .testTarget(name: "FittUITests", dependencies: ["FittUI", "FittAPI", "FittCore"]),
    ]
)
