import Foundation
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
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
        director.selectWorkers(Array(selected).sorted())
        if primary == nil, let first = selected.sorted().first { setPrimary(first) }
        sync()
    }

    func setPrimary(_ id: String) {
        primary = id; director.setPrimary(id); sync()
    }

    func follow() {
        _ = director.requestFollow(); statusText = "FOLLOW aktivní v mock režimu"; sync()
    }

    func orbitRight() {
        let i = director.requestOrbitRight(); pendingText = i.description; sync()
    }

    func ropeMode() {
        guard let primary else { statusText = "Nejdřív vyber PRIMARY pracovníka"; return }
        director.setRopeMode(workerID: primary); ropeWorkers.insert(primary); statusText = "ROPE MODE: \(primary)"; sync()
    }

    func pullAway() {
        let i = director.requestPullAway(); pendingText = i.description; sync()
    }

    func approve() {
        let floor = workers.filter { selected.contains($0.id) }.map(\.confidence).min() ?? 0
        let result = director.approvePending(context: SafetyContext(linkHealthy: true, telemetryAgeMilliseconds: 30, obstacleWarning: false, workerConfidenceFloor: floor, rcOverrideActive: false))
        switch result {
        case .approved(let v): statusText = String(format: "APPROVED  pitch %.2f  roll %.2f  yaw %.1f", v.pitchVelocity, v.rollVelocity, v.yawRate)
        case .rejected(let reason): statusText = "REJECTED: \(reason)"
        }
        pendingText = nil; sync()
    }

    func hold() { director.hold(); pendingText = nil; statusText = "HOLD"; sync() }
    func abort() { director.abort(); pendingText = nil; statusText = "ABORT – AI flight disabled"; sync() }

    func handleVoiceText(_ text: String) {
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
