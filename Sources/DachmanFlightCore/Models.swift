import Foundation

public struct NormalizedBox: Sendable, Equatable {
    public var x: Double
    public var y: Double
    public var width: Double
    public var height: Double

    public init(x: Double, y: Double, width: Double, height: Double) {
        self.x = x; self.y = y; self.width = width; self.height = height
    }

    public var centerX: Double { x + width / 2 }
    public var centerY: Double { y + height / 2 }
}

public struct WorkerTrack: Sendable, Equatable, Identifiable {
    public let id: String
    public var box: NormalizedBox
    public var confidence: Double
    public var isRopeWorker: Bool

    public init(id: String, box: NormalizedBox, confidence: Double, isRopeWorker: Bool = false) {
        self.id = id; self.box = box; self.confidence = confidence; self.isRopeWorker = isRopeWorker
    }
}

public enum FlightMode: String, Sendable, Equatable {
    case preflight
    case hold
    case follow
    case duoFollow
    case primarySecondary
    case ropeMode
    case trajectoryReview
    case executing
    case aborted
}

public enum Maneuver: String, Sendable, Equatable {
    case follow
    case orbitRight
    case orbitLeft
    case pullAway
    case hold
}

public struct FlightVector: Sendable, Equatable {
    public var pitchVelocity: Double
    public var rollVelocity: Double
    public var verticalVelocity: Double
    public var yawRate: Double
    public var gimbalPitchRate: Double

    public init(pitchVelocity: Double = 0, rollVelocity: Double = 0, verticalVelocity: Double = 0, yawRate: Double = 0, gimbalPitchRate: Double = 0) {
        self.pitchVelocity = pitchVelocity
        self.rollVelocity = rollVelocity
        self.verticalVelocity = verticalVelocity
        self.yawRate = yawRate
        self.gimbalPitchRate = gimbalPitchRate
    }

    public static let zero = FlightVector()
}

public struct FlightIntent: Sendable, Equatable {
    public let maneuver: Maneuver
    public let description: String
    public let requested: FlightVector
    public let requiresApproval: Bool
    public let ropeSafetyEnvelope: Bool

    public init(maneuver: Maneuver, description: String, requested: FlightVector, requiresApproval: Bool = true, ropeSafetyEnvelope: Bool = false) {
        self.maneuver = maneuver
        self.description = description
        self.requested = requested
        self.requiresApproval = requiresApproval
        self.ropeSafetyEnvelope = ropeSafetyEnvelope
    }
}

public struct SafetyContext: Sendable, Equatable {
    public var linkHealthy: Bool
    public var telemetryAgeMilliseconds: Int
    public var obstacleWarning: Bool
    public var workerConfidenceFloor: Double
    public var rcOverrideActive: Bool

    public init(linkHealthy: Bool = true, telemetryAgeMilliseconds: Int = 0, obstacleWarning: Bool = false, workerConfidenceFloor: Double = 1.0, rcOverrideActive: Bool = false) {
        self.linkHealthy = linkHealthy
        self.telemetryAgeMilliseconds = telemetryAgeMilliseconds
        self.obstacleWarning = obstacleWarning
        self.workerConfidenceFloor = workerConfidenceFloor
        self.rcOverrideActive = rcOverrideActive
    }
}

public enum SafetyDecision: Sendable, Equatable {
    case approved(FlightVector)
    case rejected(String)
}
