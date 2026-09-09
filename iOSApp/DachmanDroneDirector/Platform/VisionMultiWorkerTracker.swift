import Foundation
import Vision
import CoreImage
import DachmanFlightCore

/// Phase-one detector. It detects up to four humans and keeps IDs by nearest previous centre.
/// The DJI video decoder will feed CVPixelBuffer frames into this type in the hardware spike.
final class VisionMultiWorkerTracker {
    private var previous: [String: CGPoint] = [:]

    func detect(pixelBuffer: CVPixelBuffer, completion: @escaping ([WorkerTrack]) -> Void) {
        let request = VNDetectHumanRectanglesRequest { [weak self] request, _ in
            let observations = (request.results as? [VNHumanObservation] ?? []).prefix(4)
            let boxes = observations.map(\.boundingBox)
            completion(self?.assign(boxes: Array(boxes)) ?? [])
        }
        request.upperBodyOnly = false
        let handler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .up)
        DispatchQueue.global(qos: .userInitiated).async {
            try? handler.perform([request])
        }
    }

    private func assign(boxes: [CGRect]) -> [WorkerTrack] {
        var unused = boxes
        var output: [WorkerTrack] = []
        for id in previous.keys.sorted() {
            guard !unused.isEmpty, let old = previous[id] else { continue }
            let bestIndex = unused.indices.min { a, b in distance(unused[a], old) < distance(unused[b], old) }!
            let b = unused.remove(at: bestIndex)
            output.append(track(id: id, box: b))
        }
        while !unused.isEmpty {
            let b = unused.removeFirst()
            let id = "Worker \(output.count + 1)"
            output.append(track(id: id, box: b))
        }
        previous = Dictionary(uniqueKeysWithValues: output.map { ($0.id, CGPoint(x: $0.box.centerX, y: $0.box.centerY)) })
        return output
    }

    private func track(id: String, box: CGRect) -> WorkerTrack {
        WorkerTrack(id: id, box: NormalizedBox(x: box.minX, y: box.minY, width: box.width, height: box.height), confidence: 0.85)
    }
    private func distance(_ box: CGRect, _ point: CGPoint) -> CGFloat {
        let dx = box.midX - point.x, dy = box.midY - point.y
        return dx*dx + dy*dy
    }
}
