import Foundation
import Combine
import DachmanFlightCore

@MainActor
final class FlightSessionController: ObservableObject {
    @Published private(set) var state: FlightState = .grounded
    @Published private(set) var checklist: [ChecklistItem] = []
    @Published private(set) var telemetry = FlightTelemetry()
    @Published private(set) var statusMessage = "Simulátor připraven."
    @Published private(set) var trajectoryPhase = 0.0
    @Published private(set) var isAuthenticating = false
    @Published var simulatorEnabled = true
    @Published var speedProfile: FlightSpeedProfile = .standard {
        didSet { machine.setSpeedProfile(speedProfile); refresh() }
    }
    @Published var altitudeLimitMeters = 20 {
        didSet {
            machine.setAltitudeLimit(altitudeLimitMeters)
            let corrected = machine.limits.altitudeLimitMeters
            if corrected != altitudeLimitMeters { altitudeLimitMeters = corrected }
            refresh()
        }
    }

    private let machine = FlightStateMachine()
    private let authenticator = PilotAuthenticator()
    private var timer: Timer?
    private var rthTicks = 0
    private var executionTicks = 0

    init() {
        checklist = machine.checklist
        state = machine.state
        telemetry = machine.telemetry
        timer = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { [weak self] _ in
            self?.tick()
        }
    }

    deinit {
        timer?.invalidate()
    }

    func beginChecklist() {
        machine.beginChecklist()
        refresh()
    }

    func toggleChecklist(_ id: String) {
        guard let item = checklist.first(where: { $0.id == id }) else { return }
        machine.setChecklistItem(id, complete: !item.isComplete)
        refresh()
    }

    func approveTakeoffWithBiometrics() {
        guard machine.requestTakeoff() else {
            refresh()
            return
        }
        isAuthenticating = true
        Task { @MainActor in
            do {
                let verified = try await authenticator.verify(maneuver: "autonomní vzlet")
                isAuthenticating = false
                if verified {
                    _ = machine.approveTakeoff()
                    refresh()
                } else {
                    statusMessage = "Vzlet nebyl schválen."
                }
            } catch {
                isAuthenticating = false
                statusMessage = "Biometrické ověření vzletu bylo zrušeno nebo selhalo."
                refresh()
            }
        }
    }

    func requestLanding() {
        _ = machine.requestLanding()
        refresh()
    }

    func approveLanding() {
        _ = machine.approveLanding()
        refresh()
    }

    func confirmTouchdown() {
        _ = machine.confirmTouchdown()
        refresh()
    }

    func requestRTH() {
        _ = machine.requestReturnToHome()
        rthTicks = 0
        refresh()
    }

    func hold() {
        machine.hold()
        refresh()
    }

    func abort() {
        machine.abort()
        refresh()
    }

    func clearPilotOverride() {
        machine.clearPilotOverride()
        refresh()
    }

    func startDemoManeuver() {
        guard machine.state == .holding else {
            statusMessage = "Nejdřív dokonči checklist a vzlet."
            refresh()
            return
        }
        machine.approveManeuver()
        executionTicks = 0
        refresh()
    }

    private func tick() {
        guard simulatorEnabled else { return }
        trajectoryPhase = (trajectoryPhase + 0.012).truncatingRemainder(dividingBy: 1)
        switch machine.state {
        case .takingOff:
            let target = min(Double(altitudeLimitMeters), 4.0)
            let next = min(telemetry.altitudeMeters + 0.16, target)
            machine.updateTelemetry(FlightTelemetry(
                altitudeMeters: next,
                homeDistanceMeters: 0,
                batteryPercent: max(telemetry.batteryPercent - 0.01, 0),
                linkHealthy: true
            ))
            if next >= target { machine.markTakeoffComplete() }
        case .landing:
            let next = max(telemetry.altitudeMeters - 0.14, 0)
            machine.updateTelemetry(FlightTelemetry(
                altitudeMeters: next,
                homeDistanceMeters: telemetry.homeDistanceMeters,
                batteryPercent: telemetry.batteryPercent,
                linkHealthy: true
            ))
            if next <= 0 { machine.markTouchdownReady() }
        case .returningHome:
            rthTicks += 1
            let distance = max(telemetry.homeDistanceMeters - 1.2, 0)
            machine.updateTelemetry(FlightTelemetry(
                altitudeMeters: telemetry.altitudeMeters,
                homeDistanceMeters: distance,
                batteryPercent: telemetry.batteryPercent,
                linkHealthy: true
            ))
            if rthTicks > 24 { machine.markReturnToHomeComplete() }
        case .executing:
            executionTicks += 1
            if executionTicks > 35 { machine.hold() }
        default:
            break
        }
        refresh()
    }

    private func refresh() {
        state = machine.state
        checklist = machine.checklist
        telemetry = machine.telemetry
        statusMessage = machine.lastReason
    }
}
