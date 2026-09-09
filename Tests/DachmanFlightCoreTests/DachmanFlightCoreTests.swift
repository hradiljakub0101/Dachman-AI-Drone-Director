import XCTest
@testable import DachmanFlightCore

final class DachmanFlightCoreTests: XCTestCase {
    private func worker(_ id: String, x: Double) -> WorkerTrack {
        WorkerTrack(id: id, box: NormalizedBox(x: x, y: 0.3, width: 0.15, height: 0.35), confidence: 0.92)
    }

    func testTwoWorkersCanBeTrackedAndOneCanBePrimary() {
        let d = SupervisedFlightDirector()
        d.preflightPassed()
        d.updateWorkers([worker("Worker 1", x: 0.2), worker("Worker 2", x: 0.65)])
        d.selectWorkers(["Worker 1", "Worker 2"])
        d.setPrimary("Worker 2")
        XCTAssertEqual(d.primaryWorkerID, "Worker 2")
        XCTAssertEqual(d.selectedWorkerIDs.count, 2)
        XCTAssertNotNil(d.requestFollow())
    }

    func testRopeModePreservesStricterEnvelopeForOrbit() {
        let d = SupervisedFlightDirector()
        d.preflightPassed()
        d.updateWorkers([worker("Worker 1", x: 0.5)])
        d.selectWorkers(["Worker 1"])
        d.setRopeMode(workerID: "Worker 1")
        let intent = d.requestOrbitRight()
        XCTAssertTrue(intent.ropeSafetyEnvelope)
        let decision = d.approvePending(context: SafetyContext(workerConfidenceFloor: 0.9))
        if case let .approved(v) = decision {
            XCTAssertLessThanOrEqual(abs(v.rollVelocity), 0.7)
            XCTAssertLessThanOrEqual(abs(v.yawRate), 12)
        } else { XCTFail("Expected approved maneuver") }
    }

    func testPilotOverrideAlwaysRejectsAICommand() {
        let d = SupervisedFlightDirector()
        d.preflightPassed()
        d.updateWorkers([worker("Worker 1", x: 0.5)])
        d.selectWorkers(["Worker 1"])
        d.requestOrbitRight()
        let decision = d.approvePending(context: SafetyContext(workerConfidenceFloor: 0.9, rcOverrideActive: true))
        if case .rejected = decision {} else { XCTFail("RC override must reject AI control") }
    }
}
