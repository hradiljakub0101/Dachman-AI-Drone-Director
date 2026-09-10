import SwiftUI
import DachmanFlightCore

struct DirectorView: View {
    @StateObject private var vm = DirectorViewModel()
    @StateObject private var voice = VoiceDirector()
    @State private var showDJIStatus = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // DJI Status Button
                    Button(action: { showDJIStatus.toggle() }) {
                        Label("DJI Connection Status", systemImage: "antenna.radiowaves.left.and.right")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    
                    if showDJIStatus {
                        DJIStatusView()
                    }
                    
                    ZStack {
                        RoundedRectangle(cornerRadius: 18).fill(.black)
                            .frame(height: 280)
                        Text("DJI LIVE VIDEO / MOCK PREVIEW")
                            .foregroundStyle(.white.opacity(0.65))
                        ForEach(Array(vm.workers.enumerated()), id: \.element.id) { index, worker in
                            WorkerOverlay(worker: worker, primary: vm.primary == worker.id, selected: vm.selected.contains(worker.id))
                                .offset(x: index == 0 ? -80 : 80, y: index == 0 ? 10 : -5)
                        }
                    }

                    HStack {
                        Label(vm.modeText, systemImage: "airplane")
                        Spacer()
                        Text(vm.statusText).font(.caption).multilineTextAlignment(.trailing)
                    }

                    ForEach(vm.workers) { worker in
                        HStack {
                            Button(vm.selected.contains(worker.id) ? "✓ \(worker.id)" : worker.id) { vm.toggle(worker.id) }
                                .buttonStyle(.bordered)
                            if vm.primary == worker.id {
                                Button("PRIMARY") { vm.setPrimary(worker.id) }
                                    .buttonStyle(.borderedProminent)
                            } else {
                                Button("PRIMARY") { vm.setPrimary(worker.id) }
                                    .buttonStyle(.bordered)
                            }
                            Spacer()
                            Text(String(format: "%.0f %%", worker.confidence * 100)).font(.caption)
                        }
                    }

                    HStack { Button("FOLLOW") { vm.follow() }; Button("ORBIT RIGHT") { vm.orbitRight() }; Button("ROPE MODE") { vm.ropeMode() } }
                        .buttonStyle(.borderedProminent)
                    HStack { Button("PULL AWAY") { vm.pullAway() }; Button("HOLD") { vm.hold() }; Button("ABORT") { vm.abort() }.tint(.red) }
                        .buttonStyle(.bordered)

                    if let pending = vm.pendingText {
                        VStack(spacing: 8) {
                            Text("TRAJECTORY REVIEW").font(.headline)
                            Text(pending)
                            Button("APPROVE") { vm.approve() }.buttonStyle(.borderedProminent)
                        }
                        .padding().background(.thinMaterial).clipShape(RoundedRectangle(cornerRadius: 16))
                    }

                    VStack(alignment: .leading) {
                        Text("VOICE DIRECTOR").font(.headline)
                        Text(voice.transcript.isEmpty ? "Řekni například: sleduj oba pracovníky; oblet zprava; rope mode; odjezd a ukaž celou budovu" : voice.transcript)
                            .font(.callout)
                        HStack {
                            Button(voice.isListening ? "STOP MIC" : "START MIC") {
                                if voice.isListening { voice.stop() } else { voice.start() }
                            }
                            Button("POUŽÍT POVEL") { vm.handleVoiceText(voice.transcript) }
                        }.buttonStyle(.bordered)
                    }
                }
                .padding()
            }
            .navigationTitle("Dachman AI Drone Director")
        }
    }
}

private struct WorkerOverlay: View {
    let worker: WorkerTrack
    let primary: Bool
    let selected: Bool
    var body: some View {
        VStack(spacing: 2) {
            Text(primary ? "PRIMARY • \(worker.id)" : worker.id).font(.caption2).bold()
            RoundedRectangle(cornerRadius: 8).stroke(lineWidth: primary ? 4 : 2).frame(width: 105, height: 150)
        }
        .foregroundStyle(selected ? .green : .yellow)
    }
}

#Preview {
    DirectorView()
}
