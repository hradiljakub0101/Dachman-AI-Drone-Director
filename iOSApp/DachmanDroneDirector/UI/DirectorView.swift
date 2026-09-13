import SwiftUI
import DachmanFlightCore

struct DirectorView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var vm = DirectorViewModel()
    @StateObject private var voice = VoiceDirector()
    @StateObject private var session = FlightSessionController()
    @State private var showDJIStatus = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    HStack {
                        Label("Dachman AI Drone Director", systemImage: "airplane.circle.fill")
                            .font(.headline)
                        Spacer()
                        Text(session.state.rawValue)
                            .font(.caption2.bold())
                            .foregroundStyle(session.state == .aborted ? .red : .green)
                    }

                    ZStack {
                        RoundedRectangle(cornerRadius: 18).fill(.black)
                        VStack(spacing: 8) {
                            Label("DJI VIDEO / SIMULÁTOR", systemImage: "video.fill")
                                .foregroundStyle(.white.opacity(0.8))
                            Text("Živý DJI video vstup bude aktivní po ověření na iPhonu.")
                                .font(.caption)
                                .foregroundStyle(.white.opacity(0.55))
                        }
                        ForEach(Array(vm.workers.enumerated()), id: \.element.id) { index, worker in
                            WorkerOverlay(
                                worker: worker,
                                primary: vm.primary == worker.id,
                                selected: vm.selected.contains(worker.id)
                            )
                            .offset(x: index == 0 ? -78 : 78, y: index == 0 ? 18 : -8)
                        }
                        TrajectoryPreview(phase: session.trajectoryPhase)
                            .padding(12)
                    }
                    .frame(height: 270)

                    HStack {
                        Label("Stav: \(session.state.rawValue)", systemImage: "shield.checkered")
                        Spacer()
                        Text(String(format: "%.1f m", session.telemetry.altitudeMeters))
                            .font(.caption.monospacedDigit())
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("LETOVÉ NASTAVENÍ").font(.headline)
                        Picker("Rychlost", selection: $session.speedProfile) {
                            ForEach(FlightSpeedProfile.allCases, id: \.self) { profile in
                                Text(profile.rawValue).tag(profile)
                            }
                        }
                        .pickerStyle(.segmented)

                        HStack {
                            Text("Výškový limit")
                            Slider(
                                value: Binding(
                                    get: { Double(session.altitudeLimitMeters) },
                                    set: { session.altitudeLimitMeters = Int($0.rounded()) }
                                ),
                                in: 3...120,
                                step: 1
                            )
                            Text("\(session.altitudeLimitMeters) m")
                                .font(.caption.monospacedDigit())
                                .frame(width: 48, alignment: .trailing)
                        }
                    }
                    .padding()
                    .background(.thinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    VStack(alignment: .leading, spacing: 8) {
                        Text("BEZPEČNOSTNÍ CHECKLIST").font(.headline)
                        if session.checklist.isEmpty {
                            Text("Checklist ještě nebyl zahájen.")
                                .foregroundStyle(.secondary)
                        } else {
                            ForEach(session.checklist) { item in
                                Button {
                                    session.toggleChecklist(item.id)
                                } label: {
                                    Label(item.title, systemImage: item.isComplete ? "checkmark.circle.fill" : "circle")
                                        .foregroundStyle(item.isComplete ? .green : .primary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        Button("ZAHÁJIT NOVÝ CHECKLIST", action: session.beginChecklist)
                            .buttonStyle(.bordered)
                        Button(session.isAuthenticating ? "OVĚŘOVÁNÍ…" : "CHECKLIST + BIOMETRIE → VZLET") {
                            session.approveTakeoffWithBiometrics()
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(session.state != .checklist || session.checklist.contains(where: { !$0.isComplete }) || session.isAuthenticating)
                    }
                    .padding()
                    .background(.thinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                    HStack {
                        Button("HOLD") { session.hold() }
                        Button("MANÉVR") { session.startDemoManeuver() }
                        Button("RTH") { session.requestRTH() }
                    }
                    .buttonStyle(.borderedProminent)

                    HStack {
                        Button("PŘISTÁT") { session.requestLanding() }
                        Button("SCHVÁLIT PŘISTÁNÍ") { session.approveLanding() }
                        Button("POTVRDIT DOSEDNUTÍ") { session.confirmTouchdown() }
                    }
                    .buttonStyle(.bordered)

                    Button("ABORT – OKAMŽITĚ ZASTAVIT AI") { session.abort() }
                        .buttonStyle(.bordered)
                        .tint(.red)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("PRACOVNÍCI").font(.headline)
                        ForEach(vm.workers) { worker in
                            HStack {
                                Button(vm.selected.contains(worker.id) ? "✓ \(worker.id)" : worker.id) {
                                    vm.toggle(worker.id)
                                }
                                .buttonStyle(.bordered)
                                Spacer()
                                Text(String(format: "%.0f %%", worker.confidence * 100))
                                    .font(.caption)
                            }
                        }
                    }

                    Button {
                        showDJIStatus.toggle()
                    } label: {
                        Label("DJI připojení a živá telemetrie", systemImage: "antenna.radiowaves.left.and.right")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    if showDJIStatus { DJIStatusView() }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("HLASOVÝ REŽISÉR").font(.headline)
                        Text(voice.transcript.isEmpty ? "Řekni například: sleduj oba pracovníky; oblet zprava; rope mode; odjezd" : voice.transcript)
                            .font(.callout)
                        HStack {
                            Button(voice.isListening ? "STOP MIC" : "START MIC") {
                                if voice.isListening { voice.stop() } else { voice.start() }
                            }
                            Button("POUŽÍT POVEL") { vm.handleVoiceText(voice.transcript) }
                        }
                        .buttonStyle(.bordered)
                    }
                    .padding()
                    .background(.thinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }
                .padding()
            }
            .navigationTitle("Dachman AI")
            .onChange(of: scenePhase) { phase in
                if phase == .background {
                    vm.suspend()
                    session.hold()
                    voice.stop()
                }
            }
        }
    }
}

private struct WorkerOverlay: View {
    let worker: WorkerTrack
    let primary: Bool
    let selected: Bool

    var body: some View {
        VStack(spacing: 2) {
            Text(primary ? "PRIMARY • \(worker.id)" : worker.id)
                .font(.caption2)
                .bold()
            RoundedRectangle(cornerRadius: 8)
                .stroke(lineWidth: primary ? 4 : 2)
                .frame(width: 105, height: 150)
        }
        .foregroundStyle(selected ? .green : .yellow)
    }
}

private struct TrajectoryPreview: View {
    let phase: Double

    var body: some View {
        Canvas { context, size in
            var path = Path()
            let points = stride(from: 0.0, through: 1.0, by: 0.05).map { progress in
                CGPoint(
                    x: size.width * (0.12 + progress * 0.76),
                    y: size.height * (0.78 - sin(progress * .pi) * 0.56)
                )
            }
            path.move(to: points[0])
            for point in points.dropFirst() { path.addLine(to: point) }
            context.stroke(path, with: .color(.cyan.opacity(0.85)), style: StrokeStyle(lineWidth: 3, dash: [8, 6]))

            let progress = (phase * 0.76) + 0.12
            let marker = CGPoint(
                x: size.width * progress,
                y: size.height * (0.78 - sin((progress - 0.12) / 0.76 * .pi) * 0.56)
            )
            context.fill(Path(ellipseIn: CGRect(x: marker.x - 6, y: marker.y - 6, width: 12, height: 12)), with: .color(.white))
        }
    }
}

#Preview {
    DirectorView()
}
