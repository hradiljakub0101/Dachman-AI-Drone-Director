#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
ui = (root / "androidApp/app/src/main/java/cz/dachman/drone/director/MainActivity.java").read_text(encoding="utf-8")
flight = (root / "androidApp/app/src/main/java/cz/dachman/drone/director/FlightDirector.java").read_text(encoding="utf-8")
safety = (root / "androidApp/app/src/main/java/cz/dachman/drone/director/SafetySupervisor.java").read_text(encoding="utf-8")
map_view = (root / "androidApp/app/src/main/java/cz/dachman/drone/director/FlightMapView.java").read_text(encoding="utf-8")
manifest = (root / "androidApp/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

required = {
    "visual target selection": "VYBRAT WORKER 1" in ui and "VYBRAT WORKER 2" in ui,
    "camera-centred flight input": "tracking.targetFor(plan)" in flight,
    "visual target safety gate": "requiresVisualTarget()" in safety,
    "pilot override": "manualRearmRequired()" in ui,
    "optional safety polygons": "forbiddenPolygon" in map_view,
    "AI flight audit": "FlightAuditLog" in ui,
}
forbidden = {
    "GPS/UWB tag UI": "POLOHOVÉ TAGY" in ui,
    "worker position receiver": "ACTION_WORKER_POSITION" in ui,
    "worker map markers": "workerOneMarker" in map_view or "workerTwoMarker" in map_view,
    "worker tag permission": "WORKER_POSITION" in manifest,
    "simulated trajectory preview": "FlightPathView" in ui,
}

failed = [label for label, ok in required.items() if not ok]
failed += [label for label, present in forbidden.items() if present]
if failed:
    raise SystemExit("Visual tracking integration check failed: " + ", ".join(failed))
print("Camera-only Worker tracking, optional polygons and UI removal checks passed")
