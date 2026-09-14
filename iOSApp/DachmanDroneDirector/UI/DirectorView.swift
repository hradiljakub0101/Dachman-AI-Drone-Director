import SwiftUI
import DachmanFlightCore

struct DirectorView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var vm = DirectorViewModel()
    @StateObject private var voice = VoiceDirector()
    @StateObject private var session = FlightSessionController()
    @State private var showChecklist = false
    @State private var showDJIStatus = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            GeometryReader { geometry in
                ZStack {
                    VideoBackdrop()
                    hudContent(in: geometry.size)
                }
            }
        }
        .preferredColorScheme(.dark)
        .statusBarHidden(true)
        .sheet(isPresented: $showChecklist) {
            ChecklistSheet(session: session)
        }
        .sheet(isPresented: $showDJIStatus) {
            NavigationStack { DJIStatusView().navigationTitle("DJI Status") }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                vm.suspend()
                session.hold()
                voice.stop()
            }
        }
    }

    @ViewBuilder
    private func hudContent(in size: CGSize) -> some View {
        VStack(spacing: 7) {
            topHUD
            HStack(alignment: .top, spacing: 6) {
                TelemetryPanel(telemetry: session.telemetry)
                Spacer()
                CompassView()
            }
            .padding(.horizontal, 8)

            ZStack {
                ForEach(Array(vm.workers.enumerated()), id: \.element.id) { index, worker in
                    WorkerOverlay(
                        worker: worker,
                        primary: vm.primary == worker.id,
                        selected: vm.selected.contains(worker.id)
                    )
                    .position(
                        x: size.width * (index == 0 ? 0.29 : 0.71),
                        y: size.height * (index == 0 ? 0.37 : 0.44)
                    )
                }

                TrajectoryPreview(phase: session.trajectoryPhase)
                    .allowsHitTesting(false)

                if vm.selected.isEmpty {
                    HUDCallout(title: "SELECT TARGET", subtitle: "Tap a worker to begin")
                } else {
                    HUDCallout(title: session.state == .holding ? "READY TO FLY" : session.state.rawValue, subtitle: session.statusMessage)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .overlay(alignment: .trailing) {
                VStack(spacing: 6) {
                    HUDSideButton(title: "2×", action: { session.speedProfile = .cinematic })
                    HUDSideButton(title: "1×", action: { session.speedProfile = .standard })
                    HUDSideButton(title: "0.5×", action: { session.speedProfile = .precise })
                }
                .padding(.trailing, 7)
            }

            HStack(alignment: .bottom, spacing: 7) {
                MiniMapView(phase: session.trajectoryPhase)
                SafetyCard(state: session.state, telemetry: session.telemetry)
            }
            .padding(.horizontal, 8)

            modeBar
            bottomStatusBar
        }
        .padding(.top, 7)
        .padding(.bottom, 5)
    }

    private var topHUD: some View {
        HStack(spacing: 8) {
            Label("REAL FOOTAGE • CONTROL HUD DEMO", systemImage: "video.fill")
                .font(.system(size: 9, weight: .bold, design: .rounded))
                .foregroundStyle(Color.hudGreen)
                .lineLimit(1)
            Spacer()
            VStack(spacing: 0) {
                Text("Dachman AI")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                Text("Drone Director")
                    .font(.system(size: 8, weight: .medium, design: .rounded))
                    .foregroundStyle(.white.opacity(0.65))
            }
            Spacer()
            HStack(spacing: 7) {
                Circle().fill(.red).frame(width: 7, height: 7)
                Text("REC 00:09")
                Text("44%")
                    .foregroundStyle(Color.hudGreen)
            }
            .font(.system(size: 9, weight: .bold, design: .monospaced))
        }
        .padding(.horizontal, 8)
        .foregroundStyle(.white)
    }

    private var modeBar: some View {
        HStack(spacing: 3) {
            HUDModeButton(title: "FOLLOW", icon: "person.fill", action: vm.follow)
            HUDModeButton(title: "ORBIT", icon: "arrow.triangle.2.circlepath", action: vm.orbitRight)
            HUDModeButton(title: "DUO", icon: "person.2.fill") {
                vm.handleVoiceText("sleduj oba pracovníky")
            }
            HUDModeButton(title: "ROPE", icon: "figure.climbing") { vm.ropeMode() }
            HUDModeButton(title: "PULL", icon: "arrow.up.right") { vm.pullAway() }
            HUDModeButton(title: "RTH", icon: "house.fill") { session.requestRTH() }
            HUDModeButton(title: "ABORT", icon: "xmark.octagon.fill", color: .red) {
                vm.abort()
                session.abort()
            }
        }
        .padding(.horizontal, 6)
    }

    private var bottomStatusBar: some View {
        HStack(spacing: 7) {
            Button {
                showChecklist = true
            } label: {
                Label("CHECKLIST", systemImage: "checklist")
            }
            .buttonStyle(.plain)
            .foregroundStyle(Color.hudGreen)

            Spacer()

            Button {
                showDJIStatus = true
            } label: {
                Label("DJI", systemImage: "antenna.radiowaves.left.and.right")
            }
            .buttonStyle(.plain)
            .foregroundStyle(.white.opacity(0.75))

            Button {
                session.hold()
            } label: {
                Label("HOLD", systemImage: "pause.circle.fill")
            }
            .buttonStyle(.plain)
            .foregroundStyle(session.state == .holding ? Color.hudGreen : .orange)
        }
        .font(.system(size: 10, weight: .bold, design: .rounded))
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.black.opacity(0.72))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(.white.opacity(0.2)))
        .padding(.horizontal, 6)
    }
}

private struct VideoBackdrop: View {
    var body: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(red: 0.05, green: 0.11, blue: 0.08),
                    Color(red: 0.20, green: 0.28, blue: 0.23),
                    Color(red: 0.07, green: 0.12, blue: 0.16)
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Image(systemName: "building.2.crop.circle")
                .font(.system(size: 230, weight: .thin))
                .foregroundStyle(.white.opacity(0.06))
                .blur(radius: 1)
            Rectangle()
                .fill(
                    LinearGradient(
                        colors: [.black.opacity(0.05), .black.opacity(0.62)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
        }
        .ignoresSafeArea()
    }
}

private struct TelemetryPanel: View {
    let telemetry: FlightTelemetry

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("ALT  \(String(format: "%.1f m", telemetry.altitudeMeters))")
            Text("DST  \(String(format: "%.1f m", telemetry.homeDistanceMeters))")
            Text("SPD  \(telemetry.linkHealthy ? "0.0 m/s" : "LINK LOST")")
        }
        .font(.system(size: 9, weight: .bold, design: .monospaced))
        .foregroundStyle(.white.opacity(0.9))
        .padding(7)
        .background(.black.opacity(0.55))
        .overlay(RoundedRectangle(cornerRadius: 5).stroke(.white.opacity(0.25)))
    }
}

private struct CompassView: View {
    var body: some View {
        ZStack {
            Circle().stroke(.white.opacity(0.7), lineWidth: 1)
            Circle().stroke(.white.opacity(0.2), lineWidth: 8)
            Image(systemName: "location.north.fill")
                .foregroundStyle(Color.hudGreen)
                .font(.system(size: 17))
            Text("N").font(.system(size: 8, weight: .bold, design: .monospaced)).offset(y: -25)
        }
        .frame(width: 56, height: 56)
        .background(.black.opacity(0.35), in: Circle())
    }
}

private struct HUDCallout: View {
    let title: String
    let subtitle: String

    var body: some View {
        VStack(spacing: 2) {
            Label(title, systemImage: "scope")
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundStyle(Color.hudGreen)
            Text(subtitle)
                .font(.system(size: 8, weight: .medium, design: .rounded))
                .foregroundStyle(.white.opacity(0.75))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 9)
        .background(.black.opacity(0.68), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.hudGreen.opacity(0.75)))
    }
}

private struct WorkerOverlay: View {
    let worker: WorkerTrack
    let primary: Bool
    let selected: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(primary ? "WORKER 1" : worker.id.uppercased())
                .font(.system(size: 9, weight: .black, design: .rounded))
                .padding(.horizontal, 5)
                .padding(.vertical, 2)
                .background(selected ? Color.hudGreen : .orange, in: RoundedRectangle(cornerRadius: 3))
                .foregroundStyle(.black)
            RoundedRectangle(cornerRadius: 7)
                .stroke(selected ? Color.hudGreen : .yellow, lineWidth: primary ? 3 : 2)
                .frame(width: 92, height: 134)
        }
        .shadow(color: selected ? Color.hudGreen.opacity(0.8) : .clear, radius: 8)
    }
}

private struct TrajectoryPreview: View {
    let phase: Double

    var body: some View {
        Canvas { context, size in
            var path = Path()
            let points = stride(from: 0.0, through: 1.0, by: 0.05).map { progress in
                CGPoint(
                    x: size.width * (0.08 + progress * 0.84),
                    y: size.height * (0.78 - sin(progress * .pi) * 0.50)
                )
            }
            path.move(to: points[0])
            for point in points.dropFirst() { path.addLine(to: point) }
            context.stroke(path, with: .color(Color.hudGreen.opacity(0.8)), style: StrokeStyle(lineWidth: 2, dash: [7, 6]))

            let x = size.width * (0.08 + phase * 0.84)
            let y = size.height * (0.78 - sin(phase * .pi) * 0.50)
            context.fill(
                Path(ellipseIn: CGRect(x: x - 5, y: y - 5, width: 10, height: 10)),
                with: .color(.white)
            )
        }
    }
}

private struct MiniMapView: View {
    let phase: Double

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8).fill(.black.opacity(0.72))
            Canvas { context, size in
                var road = Path()
                road.move(to: CGPoint(x: 8, y: size.height - 10))
                road.addLine(to: CGPoint(x: size.width * 0.45, y: size.height * 0.55))
                road.addLine(to: CGPoint(x: size.width - 10, y: 8))
                context.stroke(road, with: .color(.white.opacity(0.35)), lineWidth: 2)
                let point = CGPoint(x: 10 + phase * (size.width - 20), y: size.height - 12 - phase * (size.height - 25))
                context.fill(Path(ellipseIn: CGRect(x: point.x - 4, y: point.y - 4, width: 8, height: 8)), with: .color(Color.hudGreen))
            }
        }
        .frame(width: 96, height: 72)
        .overlay(Text("MAP").font(.system(size: 7, weight: .bold)).foregroundStyle(.white.opacity(0.6)).padding(4), alignment: .topLeading)
    }
}

private struct SafetyCard: View {
    let state: FlightState
    let telemetry: FlightTelemetry

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Label(state == .aborted ? "ABORT" : "PILOT SAFE", systemImage: state == .aborted ? "xmark.octagon.fill" : "checkmark.shield.fill")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(state == .aborted ? .red : Color.hudGreen)
            Text(telemetry.rcOverrideActive ? "RC-N1 override active" : "Manual override ready")
            Text(telemetry.linkHealthy ? "Link healthy" : "Link lost")
            Text(telemetry.obstacleWarning ? "Obstacle warning" : "Obstacle scan pending")
        }
        .font(.system(size: 8, weight: .medium, design: .rounded))
        .foregroundStyle(.white.opacity(0.8))
        .padding(8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.black.opacity(0.72), in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.hudGreen.opacity(0.45)))
    }
}

private struct HUDSideButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(title, action: action)
            .font(.system(size: 10, weight: .bold, design: .monospaced))
            .frame(width: 40, height: 34)
            .foregroundStyle(.white)
            .background(.black.opacity(0.7), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(.white.opacity(0.35)))
    }
}

private struct HUDModeButton: View {
    let title: String
    let icon: String
    var color: Color = Color.hudGreen
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Image(systemName: icon).font(.system(size: 11))
                Text(title).font(.system(size: 7, weight: .bold, design: .rounded))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 42)
            .foregroundStyle(color)
            .background(.black.opacity(0.75), in: RoundedRectangle(cornerRadius: 7))
            .overlay(RoundedRectangle(cornerRadius: 7).stroke(color.opacity(0.6)))
        }
    }
}

private struct ChecklistSheet: View {
    @ObservedObject var session: FlightSessionController
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Před vzletem musí být potvrzen každý bod.")
                        .font(.callout)
                        .foregroundStyle(.secondary)

                    Picker("Rychlost", selection: $session.speedProfile) {
                        ForEach(FlightSpeedProfile.allCases, id: \.self) { profile in
                            Text(profile.rawValue).tag(profile)
                        }
                    }
                    .pickerStyle(.segmented)

                    VStack(alignment: .leading) {
                        Text("Výškový limit: \(session.altitudeLimitMeters) m")
                        Slider(
                            value: Binding(
                                get: { Double(session.altitudeLimitMeters) },
                                set: { session.altitudeLimitMeters = Int($0.rounded()) }
                            ),
                            in: 3...120,
                            step: 1
                        )
                    }

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

                    Button("ZAHÁJIT / RESETOVAT CHECKLIST") {
                        session.beginChecklist()
                    }
                    .buttonStyle(.bordered)

                    Button(session.isAuthenticating ? "OVĚŘOVÁNÍ…" : "BIOMETRIE A SCHVÁLIT VZLET") {
                        session.approveTakeoffWithBiometrics()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(session.state != .checklist || session.checklist.contains(where: { !$0.isComplete }) || session.isAuthenticating)

                    Text(session.statusMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding()
            }
            .navigationTitle("Flight Checklist")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Hotovo") { dismiss() }
                }
            }
        }
    }
}

private extension Color {
    static let hudGreen = Color(red: 0.25, green: 1.0, blue: 0.42)
}

#Preview {
    DirectorView()
}
