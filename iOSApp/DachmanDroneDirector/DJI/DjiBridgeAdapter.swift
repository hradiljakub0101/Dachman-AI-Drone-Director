import Foundation
import DachmanFlightCore

@MainActor
final class DjiBridgeAdapter: ObservableObject {
    @Published private(set) var status = "DJI not registered"
    @Published private(set) var pilotOverride = false
    private let bridge = DJIMini2Bridge()

    init() {
        bridge.statusBlock = { [weak self] text in DispatchQueue.main.async { self?.status = text } }
        bridge.pilotOverrideBlock = { [weak self] in DispatchQueue.main.async { self?.pilotOverride = true } }
    }

    func register() { bridge.registerSDK() }
    func armVirtualStick() { pilotOverride = false; bridge.enableVirtualStick() }
    func disarm() { bridge.disableVirtualStick() }
    func send(_ vector: FlightVector) {
        bridge.sendPitch(Float(vector.pitchVelocity), roll: Float(vector.rollVelocity), yawRate: Float(vector.yawRate), vertical: Float(vector.verticalVelocity))
    }
}
