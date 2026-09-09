import Foundation

public final class SupervisedFlightDirector: @unchecked Sendable {
    public private(set) var mode: FlightMode = .preflight
    public private(set) var workers: [String: WorkerTrack] = [:]
    public private(set) var selectedWorkerIDs: [String] = []
    public private(set) var primaryWorkerID: String?
    public private(set) var pendingIntent: FlightIntent?
    public private(set) var ropeWorkerIDs: Set<String> = []

    private let planner = CinematicPlanner()
    private let safety = SafetySupervisor()

    public init() {}

    public func preflightPassed() { mode = .hold }

    public func updateWorkers(_ tracks: [WorkerTrack]) {
        workers = Dictionary(uniqueKeysWithValues: tracks.map { ($0.id, $0) })
    }

    public func selectWorkers(_ ids: [String]) {
        selectedWorkerIDs = Array(ids.prefix(4)).filter { workers[$0] != nil }
        if primaryWorkerID == nil { primaryWorkerID = selectedWorkerIDs.first }
    }

    public func setPrimary(_ id: String) {
        guard workers[id] != nil else { return }
        primaryWorkerID = id
        if !selectedWorkerIDs.contains(id) { selectedWorkerIDs.insert(id, at: 0) }
        mode = selectedWorkerIDs.count > 1 ? .primarySecondary : .follow
    }

    public func setRopeMode(workerID: String?) {
        if let workerID { ropeWorkerIDs.insert(workerID) }
        else if let primaryWorkerID { ropeWorkerIDs.insert(primaryWorkerID) }
        mode = .ropeMode
    }

    public func requestFollow() -> FlightIntent? {
        guard let primaryID = primaryWorkerID, let primary = workers[primaryID] else { return nil }
        let secondary = selectedWorkerIDs.first(where: { $0 != primaryID }).flatMap { workers[$0] }
        let rope = ropeWorkerIDs.contains(primaryID)
        let intent = planner.follow(primary: primary, secondary: secondary, ropeSafety: rope)
        mode = secondary == nil ? (rope ? .ropeMode : .follow) : (rope ? .ropeMode : .duoFollow)
        return intent
    }

    public func requestOrbitRight() -> FlightIntent {
        let intent = planner.orbitRight(ropeSafety: isRopeEnvelopeActive)
        pendingIntent = intent; mode = .trajectoryReview; return intent
    }

    public func requestOrbitLeft() -> FlightIntent {
        let intent = planner.orbitLeft(ropeSafety: isRopeEnvelopeActive)
        pendingIntent = intent; mode = .trajectoryReview; return intent
    }

    public func requestPullAway() -> FlightIntent {
        let intent = planner.pullAway(ropeSafety: isRopeEnvelopeActive)
        pendingIntent = intent; mode = .trajectoryReview; return intent
    }

    public func approvePending(context: SafetyContext) -> SafetyDecision {
        guard let pendingIntent else { return .rejected("No trajectory is pending") }
        let result = safety.evaluate(pendingIntent, context: context)
        switch result {
        case .approved:
            mode = .executing
            self.pendingIntent = nil
        case .rejected:
            mode = .hold
            self.pendingIntent = nil
        }
        return result
    }

    public func evaluateContinuous(_ intent: FlightIntent, context: SafetyContext) -> SafetyDecision {
        safety.evaluate(intent, context: context)
    }

    public func hold() { pendingIntent = nil; mode = .hold }
    public func abort() { pendingIntent = nil; mode = .aborted }

    private var isRopeEnvelopeActive: Bool {
        guard let primaryWorkerID else { return false }
        return ropeWorkerIDs.contains(primaryWorkerID) || mode == .ropeMode
    }
}
