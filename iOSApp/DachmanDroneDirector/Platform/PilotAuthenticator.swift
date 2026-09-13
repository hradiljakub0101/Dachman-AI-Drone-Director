import LocalAuthentication

/// Per-action device-owner verification, not a named pilot account or licence.
@MainActor
final class PilotAuthenticator {
    private var activeContext: LAContext?
    func verify(maneuver: String) async throws -> Bool {
        cancel()
        let context = LAContext()
        activeContext = context
        defer { if activeContext === context { activeContext = nil } }
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            throw error ?? NSError(domain: "PilotAuthentication", code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Nastavte kód zařízení nebo biometrii."])
        }
        return try await context.evaluatePolicy(.deviceOwnerAuthentication,
            localizedReason: "Potvrďte manévr: \(maneuver)")
    }
    func cancel() { activeContext?.invalidate(); activeContext = nil }
}
