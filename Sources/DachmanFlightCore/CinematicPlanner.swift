import Foundation

public struct CinematicPlanner: Sendable {
    public init() {}

    public func follow(primary: WorkerTrack, secondary: WorkerTrack? = nil, ropeSafety: Bool = false) -> FlightIntent {
        let targetX: Double = secondary == nil ? 0.62 : 0.50
        let targetY: Double = 0.50
        let observedX = secondary.map { (primary.box.centerX + $0.box.centerX) / 2 } ?? primary.box.centerX
        let observedY = secondary.map { (primary.box.centerY + $0.box.centerY) / 2 } ?? primary.box.centerY
        let xError = targetX - observedX
        let yError = targetY - observedY
        return FlightIntent(
            maneuver: .follow,
            description: secondary == nil ? "Follow primary worker" : "Keep both workers in composition",
            requested: FlightVector(
                pitchVelocity: 0.25,
                rollVelocity: xError * 1.2,
                verticalVelocity: yError * 0.6,
                yawRate: xError * 18,
                gimbalPitchRate: -yError * 10
            ),
            requiresApproval: false,
            ropeSafetyEnvelope: ropeSafety
        )
    }

    public func orbitRight(ropeSafety: Bool) -> FlightIntent {
        FlightIntent(
            maneuver: .orbitRight,
            description: "Slow cinematic orbit to the right",
            requested: FlightVector(pitchVelocity: 0.20, rollVelocity: 0.65, verticalVelocity: 0, yawRate: -9, gimbalPitchRate: 0),
            requiresApproval: true,
            ropeSafetyEnvelope: ropeSafety
        )
    }

    public func orbitLeft(ropeSafety: Bool) -> FlightIntent {
        FlightIntent(
            maneuver: .orbitLeft,
            description: "Slow cinematic orbit to the left",
            requested: FlightVector(pitchVelocity: 0.20, rollVelocity: -0.65, verticalVelocity: 0, yawRate: 9, gimbalPitchRate: 0),
            requiresApproval: true,
            ropeSafetyEnvelope: ropeSafety
        )
    }

    public func pullAway(ropeSafety: Bool) -> FlightIntent {
        FlightIntent(
            maneuver: .pullAway,
            description: "Pull away and reveal the building",
            requested: FlightVector(pitchVelocity: -0.7, rollVelocity: 0, verticalVelocity: 0.45, yawRate: 0, gimbalPitchRate: -2),
            requiresApproval: true,
            ropeSafetyEnvelope: ropeSafety
        )
    }
}
