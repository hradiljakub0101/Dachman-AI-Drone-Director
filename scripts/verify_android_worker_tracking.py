#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
checks = {
    "signed positioning permission": (root / "androidApp/app/src/main/AndroidManifest.xml", 'protectionLevel="signature"'),
    "position receiver": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/MainActivity.java", "ACTION_WORKER_POSITION"),
    "hybrid controller": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/HybridFollowController.java", "Low-gain geographic feed-forward"),
    "standoff guard": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/SafetySupervisor.java", "MINIMUM_WORKER_STANDOFF_METERS"),
    "stale tag guard": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/WorkerPositionFix.java", "isFresh"),
    "pilot override": (root / "androidApp/app/src/dji/java/cz/dachman/drone/director/DjiConnection.java", "postPilotOverride"),
    "worker map markers": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/FlightMapView.java", "workerOneMarker"),
    "roof and forbidden polygons": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/FlightMapView.java", "forbiddenPolygon"),
    "appearance identity": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/AppearanceSignature.java", "upper clothing"),
    "fusion state": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/FusionStatus.java", "FUSION_OK"),
    "flight audit": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/FlightAuditLog.java", "flight-ai-audit.jsonl"),
    "roof plane": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/SiteSafetyPlan.java", "roofHeightAt"),
    "overflight guard": (root / "androidApp/app/src/main/java/cz/dachman/drone/director/SafetySupervisor.java", "Zakázaný přelet"),
}

failed = []
for label, (path, needle) in checks.items():
    if not path.is_file() or needle not in path.read_text(encoding="utf-8"):
        failed.append(label)
if failed:
    raise SystemExit("Missing worker tracking integration: " + ", ".join(failed))
print("Worker visual/GPS/UWB integration checks passed")
