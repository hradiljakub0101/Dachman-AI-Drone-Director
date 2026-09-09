import Foundation

public enum VoiceCommand: Sendable, Equatable {
    case followBoth
    case makePrimary(String)
    case orbitRight
    case orbitLeft
    case ropeMode(String?)
    case pullAway
    case hold
    case abort
    case unknown
}

public struct CzechVoiceIntentParser: Sendable {
    public init() {}

    public func parse(_ text: String) -> VoiceCommand {
        let s = text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "cs_CZ"))
        if s.contains("abort") || s.contains("zrus") || s.contains("prevezmi") || s.contains("stop") { return .abort }
        if s.contains("drz") || s.contains("hold") { return .hold }
        if s.contains("oba pracovnik") || s.contains("oba delnik") || s.contains("sleduj oba") { return .followBoth }
        if s.contains("oblet") && s.contains("zprava") { return .orbitRight }
        if s.contains("oblet") && s.contains("zleva") { return .orbitLeft }
        if s.contains("odjezd") || s.contains("pull away") || s.contains("ukaz celou budovu") { return .pullAway }
        if s.contains("lano") || s.contains("rope") || s.contains("slan") { return .ropeMode(nil) }
        if s.contains("worker one") || s.contains("worker 1") || s.contains("pracovnik jedna") { return .makePrimary("Worker 1") }
        if s.contains("worker two") || s.contains("worker 2") || s.contains("pracovnik dva") { return .makePrimary("Worker 2") }
        return .unknown
    }
}
