import SwiftUI
import DachmanFlightCore

/// Status view for DJI SDK connection and device information.
/// This screen displays DJI link status but does not enable autonomous flight.
/// Autonomous flight is only enabled when a valid SafetyContext and user approval is provided.
@MainActor
final class DJIStatusViewModel: ObservableObject {
    @Published var djiStatus = "DJI SDK: Not Initialized"
    @Published var connectionStatus = "Disconnected"
    @Published var appKeyStatus = "App Key: Not configured"
    @Published var isSimulationMode = true
    
    private let bridge = DJIMini2Bridge()
    
    init() {
        setupDJIStatusMonitoring()
    }
    
    private func setupDJIStatusMonitoring() {
        bridge.statusBlock = { [weak self] status in
            DispatchQueue.main.async {
                self?.djiStatus = status
                self?.updateConnectionStatus(from: status)
            }
        }
    }
    
    private func updateConnectionStatus(from status: String) {
        if status.lowercased().contains("connected") {
            connectionStatus = "✓ Connected"
        } else if status.lowercased().contains("disconnected") {
            connectionStatus = "✗ Disconnected"
        } else if status.lowercased().contains("registering") || status.lowercased().contains("connecting") {
            connectionStatus = "⟳ Connecting..."
        } else {
            connectionStatus = "? Unknown"
        }
    }
    
    func initializeDJI() {
        let appKey = DJIConfigManager.getAppKey()
        if appKey == nil || appKey?.isEmpty ?? true {
            djiStatus = "DJI SDK: App Key not configured"
            appKeyStatus = "App Key: ⚠ Missing - cannot initialize"
            return
        }
        appKeyStatus = "App Key: ✓ Loaded"
        bridge.registerSDK()
    }
}

struct DJIStatusView: View {
    @StateObject private var vm = DJIStatusViewModel()
    
    var body: some View {
        VStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Text("DJI Mini 2 Connection").font(.headline)
                
                HStack {
                    Text("Status:")
                    Spacer()
                    Text(vm.connectionStatus)
                        .font(.caption)
                        .foregroundStyle(vm.connectionStatus.contains("Connected") ? .green : .orange)
                }
                
                HStack {
                    Text("Mode:")
                    Spacer()
                    HStack(spacing: 8) {
                        Text(vm.isSimulationMode ? "🎮 Simulation" : "🚁 Live")
                            .font(.caption)
                        Toggle("", isOn: $vm.isSimulationMode)
                            .labelsHidden()
                    }
                }
                
                Text(vm.djiStatus)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                
                Text(vm.appKeyStatus)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding()
            .background(.thinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            
            VStack(spacing: 8) {
                Button(action: vm.initializeDJI) {
                    Label("Connect to DJI", systemImage: "antenna.radiowaves.left.and.right")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                
                Text("⚠ Autonomous flight disabled. Safety Supervisor active in all modes.")
                    .font(.caption)
                    .foregroundStyle(.orange)
                    .multilineTextAlignment(.center)
            }
        }
        .padding()
    }
}

#Preview {
    DJIStatusView()
}
