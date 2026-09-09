// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "DachmanFlightCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "DachmanFlightCore", targets: ["DachmanFlightCore"])
    ],
    targets: [
        .target(name: "DachmanFlightCore"),
        .testTarget(name: "DachmanFlightCoreTests", dependencies: ["DachmanFlightCore"])
    ]
)
