import Foundation
import Combine
import DachmanFlightCore

@MainActor
final class DirectorViewModel: ObservableObject {
    @Published var modeText = "PREFLIGHT"
    @Published var workers: [WorkerTrack] = []
    @Published var selected: Set<String> = []
    @Published var primary: String?
    @Published var pendingText: String?
    @Published var statusText = "Mock mode – DJI bridge is not armed"
    @Published var ropeWorkers: Set<String> = []

    @Published private(set) var isAuthenticating = false
    private let authenticator = PilotAuthenticator()
    private var approvalRevision = 0

    private func invalidateApproval() {
        approvalRevision += 1
        authenticator.cancel()
        isAuthenticating = false
    }

    func suspend() { hold() }

    private let director = SupervisedFlightDirector()
    private let voiceParser = CzechVoiceIntentParser()

    init() {
        director.preflightPassed()
        seedMockWorkers()
        sync()
    }

    func seedMockWorkers() {
        workers = [
            WorkerTrack(id: "Worker 1", box: NormalizedBox(x: 0.18, y: 0.28, width: 0.17, height: 0.38), confidence: 0.94),
            WorkerTrack(id: "Worker 2", box: NormalizedBox(x: 0.62, y: 0.30, width: 0.16, height: 0.36), confidence: 0.91)
        ]
        director.updateWorkers(workers)
    }

    func toggle(_ id: String) {
        invalidateApproval();
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
        director.selectWorkers(Array(selected).sorted())
        if primary == nil, let first = selected.sorted().first { setPrimary(first) }
        sync()
    }

    func setPrimary(_ id: String) {
        invalidateApproval();
        primary = id; director.setPrimary(id); sync()
    }

    func follow() {
        invalidateApproval();
        _ = director.requestFollow(); statusText = "FOLLOW aktivní v mock režimu"; sync()
    }

    func orbitRight() {
        invalidateApproval();
        let i = director.requestOrbitRight(); pendingText = i.description; sync()
    }

    func ropeMode() {
        invalidateApproval();
        guard let primary else { statusText = "Nejdřív vyber PRIMARY pracovníka"; return }
        director.setRopeMode(workerID: primary); ropeWorkers.insert(primary); statusText = "ROPE MODE: \(primary)"; sync()
    }

    func pullAway() {
        invalidateApproval();
        let i = director.requestPullAway(); pendingText = i.description; sync()
    }

    func approve() {
        guard !isAuthenticating, let intent = director.pendingIntent else { return }
        let revision = approvalRevision
        isAuthenticating = true
        Task { @MainActor in
            do {
                let verified = try await authenticator.verify(maneuver: intent.description)
                guard revision == approvalRevision else { return }
                isAuthenticating = false
                guard verified, director.pendingIntent == intent else {
                    statusText = "Ověření nebylo dokončeno"; return
                }
                approveVerifiedMockIntent()
            } catch {
                guard revision == approvalRevision else { return }
                isAuthenticating = false
                statusText = "Manévr neschválen: ověření držitele zařízení selhalo nebo bylo zrušeno"
            }
        }
    }

    private func approveVerifiedMockIntent() {
        // Demonstration only: no live telemetry and no bridge command dispatch.
        let floor = workers.filter { selected.contains($0.id) }.map(\.confidence).min() ?? 0
        let result = director.approvePending(context: SafetyContext(linkHealthy: true, telemetryAgeMilliseconds: 30, obstacleWarning: false, workerConfidenceFloor: floor, rcOverrideActive: false))
        switch result {
        case .approved(let v): statusText = String(format: "SIMULACE – OVĚŘENO  pitch %.2f  roll %.2f  yaw %.1f", v.pitchVelocity, v.rollVelocity, v.yawRate)
        case .rejected(let reason): statusText = "REJECTED: \(reason)"
        }
        pendingText = nil; sync()
    }

    func hold() {
        invalidateApproval(); director.hold(); pendingText = nil; statusText = "HOLD"; sync() }
    func abort() {
        invalidateApproval(); director.abort(); pendingText = nil; statusText = "ABORT – AI flight disabled"; sync() }

    func handleVoiceText(_ text: String) {
        invalidateApproval();
        switch voiceParser.parse(text) {
        case .followBoth: workers.forEach { selected.insert($0.id) }; director.selectWorkers(Array(selected)); follow()
        case .makePrimary(let id): setPrimary(id)
        case .orbitRight: orbitRight()
        case .orbitLeft: let i = director.requestOrbitLeft(); pendingText = i.description; sync()
        case .ropeMode: ropeMode()
        case .pullAway: pullAway()
        case .hold: hold()
        case .abort: abort()
        case .unknown: statusText = "Hlasový povel nerozpoznán"
        }
    }

    private func sync() { modeText = director.mode.rawValue.uppercased() }
}
