import Foundation
import Combine
import Speech
import AVFoundation

@MainActor
final class VoiceDirector: ObservableObject {
    @Published var transcript = ""
    @Published var isListening = false

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "cs_CZ"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func start() {
        SFSpeechRecognizer.requestAuthorization { [weak self] status in
            Task { @MainActor in if status == .authorized { self?.begin() } }
        }
    }

    private func begin() {
        stop()
        let req = SFSpeechAudioBufferRecognitionRequest()
        req.shouldReportPartialResults = true
        request = req
        let node = engine.inputNode
        let format = node.outputFormat(forBus: 0)
        node.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in req.append(buffer) }
        task = recognizer?.recognitionTask(with: req) { [weak self] result, _ in
            Task { @MainActor in
                if let result { self?.transcript = result.bestTranscription.formattedString }
            }
        }
        do { engine.prepare(); try engine.start(); isListening = true }
        catch { transcript = "Microphone error: \(error.localizedDescription)" }
    }

    func stop() {
        if engine.isRunning { engine.stop(); engine.inputNode.removeTap(onBus: 0) }
        request?.endAudio(); task?.cancel(); request = nil; task = nil; isListening = false
    }
}
