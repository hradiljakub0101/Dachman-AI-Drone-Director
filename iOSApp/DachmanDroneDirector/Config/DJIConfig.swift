import Foundation

/// DJI MSDK 4 reads this exact key from the built bundle during registration.
enum DJIConfig {
    static func loadAppKey() -> String? {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "DJISDKAppKey") as? String,
              !key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !key.contains("__"), !key.contains("$("),
              !key.lowercased().contains("placeholder") else { return nil }
        return key
    }
}
