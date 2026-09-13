import Foundation

public enum FlightSpeedProfile: String, CaseIterable, Sendable, Equatable {
    case precise = "PŘESNÝ"
    case standard = "STANDARDNÍ"
    case cinematic = "FILMOVÝ"

    public var horizontalLimitMetersPerSecond: Double {
        switch self {
        case .precise: return 0.45
        case .standard: return 0.90
        case .cinematic: return 1.35
        }
    }

    public var verticalLimitMetersPerSecond: Double {
        switch self {
        case .precise: return 0.30
        case .standard: return 0.60
        case .cinematic: return 0.90
        }
    }

    public var yawLimitDegreesPerSecond: Double {
        switch self {
        case .precise: return 10
        case .standard: return 18
        case .cinematic: return 25
        }
    }
}

public struct FlightLimits: Sendable, Equatable {
    public static let minimumAltitudeMeters = 3
    public static let maximumAltitudeMeters = 120
    public var altitudeLimitMeters: Int

    public init(altitudeLimitMeters: Int = 20) {
        self.altitudeLimitMeters = min(max(altitudeLimitMeters, Self.minimumAltitudeMeters), Self.maximumAltitudeMeters)
    }

    public mutating func setAltitudeLimit(_ meters: Int) {
        altitudeLimitMeters = min(max(meters, Self.minimumAltitudeMeters), Self.maximumAltitudeMeters)
    }
}

public enum FlightState: String, Sendable, Equatable {
    case grounded = "NA ZEMI"
    case checklist = "CHECKLIST"
    case awaitingTakeoffApproval = "ČEKÁ NA SCHVÁLENÍ VZLETU"
    case takingOff = "VZLET"
    case holding = "HOLD"
    case executing = "PROVÁDÍ MANÉVR"
    case landingAwaitingApproval = "ČEKÁ NA SCHVÁLENÍ PŘISTÁNÍ"
    case landing = "PŘISTÁNÍ"
    case touchdownConfirmation = "POTVRĎ DOSedNUTÍ"
    case returningHome = "NÁVRAT DOMŮ"
    case landed = "PŘISTÁNO"
    case aborted = "ABORT"

    public var isAirborne: Bool {
        switch self {
        case .takingOff, .holding, .executing, .landingAwaitingApproval, .landing, .touchdownConfirmation, .returningHome:
            return true
        default:
            return false
        }
    }
}

public struct ChecklistItem: Identifiable, Sendable, Equatable {
    public let id: String
    public let title: String
    public var isComplete: Bool

    public init(id: String, title: String, isComplete: Bool = false) {
        self.id = id
        self.title = title
        self.isComplete = isComplete
    }
}

public struct FlightTelemetry: Sendable, Equatable {
    public var altitudeMeters: Double
    public var homeDistanceMeters: Double
    public var batteryPercent: Double
    public var linkHealthy: Bool
    public var rcOverrideActive: Bool
    public var obstacleWarning: Bool

    public init(
        altitudeMeters: Double = 0,
        homeDistanceMeters: Double = 0,
        batteryPercent: Double = 100,
        linkHealthy: Bool = true,
        rcOverrideActive: Bool = false,
        obstacleWarning: Bool = false
    ) {
        self.altitudeMeters = altitudeMeters
        self.homeDistanceMeters = homeDistanceMeters
        self.batteryPercent = batteryPercent
        self.linkHealthy = linkHealthy
        self.rcOverrideActive = rcOverrideActive
        self.obstacleWarning = obstacleWarning
    }
}

public final class FlightStateMachine: @unchecked Sendable {
    public private(set) var state: FlightState = .grounded
    public private(set) var speedProfile: FlightSpeedProfile = .standard
    public private(set) var limits = FlightLimits()
    public private(set) var checklist: [ChecklistItem] = FlightStateMachine.defaultChecklist
    public private(set) var telemetry = FlightTelemetry()
    public private(set) var lastReason = "Systém připraven."

    public init() {}

    public var checklistComplete: Bool {
        checklist.allSatisfy(\.isComplete)
    }

    public func beginChecklist() {
        guard state == .grounded || state == .landed || state == .aborted else { return }
        checklist = Self.defaultChecklist
        state = .checklist
        lastReason = "Dokonči všechny bezpečnostní body."
    }

    public func setChecklistItem(_ id: String, complete: Bool) {
        guard state == .checklist else { return }
        guard let index = checklist.firstIndex(where: { $0.id == id }) else { return }
        checklist[index].isComplete = complete
        lastReason = checklistComplete ? "Checklist je kompletní. Lze požádat o schválení vzletu." : "Checklist není kompletní."
    }

    public func setSpeedProfile(_ profile: FlightSpeedProfile) {
        guard !state.isAirborne else { return }
        speedProfile = profile
    }

    public func setAltitudeLimit(_ meters: Int) {
        guard !state.isAirborne else { return }
        limits.setAltitudeLimit(meters)
    }

    public func requestTakeoff() -> Bool {
        guard state == .checklist, checklistComplete else {
            lastReason = "Vzlet je zablokovaný: checklist není kompletní."
            return false
        }
        state = .awaitingTakeoffApproval
        lastReason = "Čeká na biometrické schválení vzletu."
        return true
    }

    public func approveTakeoff() -> Bool {
        guard state == .awaitingTakeoffApproval else { return false }
        state = .takingOff
        lastReason = "Simulovaný vzlet probíhá do bezpečné pracovní výšky."
        return true
    }

    public func markTakeoffComplete() {
        guard state == .takingOff else { return }
        state = .holding
        lastReason = "Vzlet dokončen. Dron je v režimu HOLD."
    }

    public func requestLanding() -> Bool {
        guard state.isAirborne else {
            lastReason = "Přistání lze požádat pouze ve vzduchu."
            return false
        }
        state = .landingAwaitingApproval
        lastReason = "Čeká na schválení přistání."
        return true
    }

    public func approveLanding() -> Bool {
        guard state == .landingAwaitingApproval else { return false }
        state = .landing
        lastReason = "Přistání probíhá. Po dosednutí bude vyžadováno potvrzení."
        return true
    }

    public func markTouchdownReady() {
        guard state == .landing else { return }
        state = .touchdownConfirmation
        lastReason = "Dron dosedl v simulaci. Potvrď dokončení přistání."
    }

    public func confirmTouchdown() -> Bool {
        guard state == .touchdownConfirmation else { return false }
        state = .landed
        lastReason = "Přistání potvrzeno."
        return true
    }

    public func requestReturnToHome() -> Bool {
        guard state.isAirborne else {
            lastReason = "RTH lze požádat pouze ve vzduchu."
            return false
        }
        state = .returningHome
        lastReason = "Návrat domů probíhá v simulaci."
        return true
    }

    public func markReturnToHomeComplete() {
        guard state == .returningHome else { return }
        state = .holding
        lastReason = "Návrat domů dokončen. Dron drží pozici nad místem vzletu."
    }

    public func approveManeuver() {
        guard state == .holding || state == .executing else { return }
        state = .executing
        lastReason = "Schválený manévr probíhá."
    }

    public func hold() {
        guard state != .grounded && state != .landed else { return }
        state = .holding
        lastReason = "HOLD: automatický pohyb zastaven."
    }

    public func abort() {
        state = .aborted
        telemetry.rcOverrideActive = true
        lastReason = "ABORT: automatické řízení zablokováno."
    }

    public func clearPilotOverride() {
        telemetry.rcOverrideActive = false
        if state == .aborted { state = .holding }
        lastReason = "Pilot override uvolněn. Vyžaduje nové schválení."
    }

    public func updateTelemetry(_ telemetry: FlightTelemetry) {
        self.telemetry = telemetry
        if telemetry.rcOverrideActive && state == .executing {
            state = .holding
            lastReason = "RC-N1 override: fyzický knipl má přednost."
        }
    }

    private static let defaultChecklist = [
        ChecklistItem(id: "battery", title: "Baterie a RC-N1 mají dostatečnou kapacitu"),
        ChecklistItem(id: "space", title: "Volný prostor a bezpečná úniková trasa"),
        ChecklistItem(id: "weather", title: "Vítr, počasí a světelné podmínky jsou přijatelné"),
        ChecklistItem(id: "target", title: "Worker 1 / Worker 2 jsou správně označeni"),
        ChecklistItem(id: "limit", title: "Výškový limit a rychlost jsou zkontrolované"),
        ChecklistItem(id: "pilot", title: "Pilot drží RC-N1 a je připraven převzít řízení")
    ]
}
