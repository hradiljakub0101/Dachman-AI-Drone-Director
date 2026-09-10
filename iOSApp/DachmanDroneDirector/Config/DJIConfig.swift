import Foundation

/// Secure configuration for DJI SDK initialization.
/// The DJI App Key is loaded from:
/// 1. Environment variable: DJI_APP_KEY (recommended for CI/CD)
/// 2. Info.plist: DJISDKAppKey (replaced at build time for device signing)
/// 3. Runtime configuration: DJIConfigManager.setAppKey(_:)
public struct DJIConfig {
    /// Load DJI App Key from environment or plist
    static func loadAppKey() -> String? {
        // First priority: Environment variable (for CI/CD and testing)
        if let envKey = ProcessInfo.processInfo.environment["DJI_APP_KEY"],
           !envKey.isEmpty,
           !envKey.contains("__"),
           !envKey.contains("placeholder") {
            return envKey
        }
        
        // Second priority: Info.plist (for device signing)
        if let plistKey = Bundle.main.infoDictionary?["DJISDKAppKey"] as? String,
           !plistKey.isEmpty,
           !plistKey.contains("__"),
           !plistKey.contains("placeholder") {
            return plistKey
        }
        
        // No valid key found
        return nil
    }
}

/// Runtime configuration manager for DJI SDK
public class DJIConfigManager {
    static var appKey: String?
    
    /// Set DJI App Key at runtime (for user-supplied keys)
    public static func setAppKey(_ key: String) {
        guard !key.isEmpty else { return }
        appKey = key
    }
    
    /// Get the current DJI App Key
    public static func getAppKey() -> String? {
        appKey ?? DJIConfig.loadAppKey()
    }
}
