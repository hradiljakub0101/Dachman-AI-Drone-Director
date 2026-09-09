# Doporučený iPhone toolchain

- macOS 26 nebo podporovaný macOS pro Xcode 26
- Xcode 26.6 (CI pin; lokálně 26.x)
- Swift 6.3 compiler, aplikace dočasně ve Swift 5 language mode pro starší Objective-C DJI framework
- SwiftUI
- Vision + Core ML (tracking)
- Speech + AVFoundation (hlas)
- DJI Mobile SDK iOS 4.16.2
- CocoaPods
- XcodeGen
- Git + GitHub Actions macOS 26

## Proč nativně

DJI řízení, MFi/USB linka, Vision, Speech a pilot override jsou bezpečnostně citlivé. První verze proto nemá Flutter ani React Native bridge.
