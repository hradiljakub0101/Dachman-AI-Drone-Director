import Foundation

public struct SafetySupervisor: Sendable {
    public var maxTelemetryAgeMilliseconds: Int = 500
    public var normalMaxHorizontalVelocity: Double = 1.5
    public var ropeMaxHorizontalVelocity: Double = 0.7
    public var normalMaxVerticalVelocity: Double = 1.0
    public var ropeMaxVerticalVelocity: Double = 0.5
    public var normalMaxYawRate: Double = 25
    public var ropeMaxYawRate: Double = 12
    public var minimumWorkerConfidence: Double = 0.65

    public init() {}

    public func evaluate(_ intent: FlightIntent, context: SafetyContext) -> SafetyDecision {
        if context.rcOverrideActive { return .rejected("Pilot override is active") }
        if !context.linkHealthy { return .rejected("DJI link is not healthy") }
        if context.telemetryAgeMilliseconds > maxTelemetryAgeMilliseconds { return .rejected("Telemetry is stale") }
        if context.obstacleWarning { return .rejected("Obstacle warning is active") }
        if context.workerConfidenceFloor < minimumWorkerConfidence { return .rejected("Worker tracking confidence is too low") }

        let rope = intent.ropeSafetyEnvelope
        let horizontal = rope ? ropeMaxHorizontalVelocity : normalMaxHorizontalVelocity
        let vertical = rope ? ropeMaxVerticalVelocity : normalMaxVerticalVelocity
        let yaw = rope ? ropeMaxYawRate : normalMaxYawRate

        let v = intent.requested
        let constrained = FlightVector(
            pitchVelocity: v.pitchVelocity.clamped(to: -horizontal...horizontal),
            rollVelocity: v.rollVelocity.clamped(to: -horizontal...horizontal),
            verticalVelocity: v.verticalVelocity.clamped(to: -vertical...vertical),
            yawRate: v.yawRate.clamped(to: -yaw...yaw),
            gimbalPitchRate: v.gimbalPitchRate.clamped(to: -20...20)
        )
        return .approved(constrained)
    }
}

private extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        min(max(self, limits.lowerBound), limits.upperBound)
    }
}
