import XCTest
@testable import DachmanFlightCore

final class FlightControlModelTests: XCTestCase {
    func testAltitudeLimitIsClampedToSafeRange() {
        var limits = FlightLimits(altitudeLimitMeters: 1)
        XCTAssertEqual(limits.altitudeLimitMeters, 3)
        limits.setAltitudeLimit(999)
        XCTAssertEqual(limits.altitudeLimitMeters, 120)
    }

    func testTakeoffRequiresCompletedChecklist() {
        let machine = FlightStateMachine()
        machine.beginChecklist()
        XCTAssertFalse(machine.requestTakeoff())
        for item in machine.checklist {
            machine.setChecklistItem(item.id, complete: true)
        }
        XCTAssertTrue(machine.requestTakeoff())
        XCTAssertTrue(machine.approveTakeoff())
        XCTAssertEqual(machine.state, .takingOff)
    }

    func testLandingRequiresTouchdownConfirmation() {
        let machine = FlightStateMachine()
        machine.beginChecklist()
        for item in machine.checklist {
            machine.setChecklistItem(item.id, complete: true)
        }
        _ = machine.requestTakeoff()
        _ = machine.approveTakeoff()
        machine.markTakeoffComplete()
        XCTAssertTrue(machine.requestLanding())
        XCTAssertTrue(machine.approveLanding())
        machine.markTouchdownReady()
        XCTAssertEqual(machine.state, .touchdownConfirmation)
        XCTAssertTrue(machine.confirmTouchdown())
        XCTAssertEqual(machine.state, .landed)
    }

    func testPilotOverrideStopsExecutingState() {
        let machine = FlightStateMachine()
        machine.beginChecklist()
        for item in machine.checklist {
            machine.setChecklistItem(item.id, complete: true)
        }
        _ = machine.requestTakeoff()
        _ = machine.approveTakeoff()
        machine.markTakeoffComplete()
        machine.approveManeuver()
        machine.updateTelemetry(FlightTelemetry(altitudeMeters: 4, rcOverrideActive: true))
        XCTAssertEqual(machine.state, .holding)
    }
}
