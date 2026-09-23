from pathlib import Path


root = Path(__file__).resolve().parents[1]
source = (root / "androidApp/app/src/main/java/cz/dachman/drone/director/FlightMapView.java").read_text()

required = {
    "Satellite basemap": "EsriWorldImagery",
    "Road-map fallback": "TileSourceFactory.MAPNIK",
    "Zoom limited to available tile levels": "map.setMaxZoomLevel(19.0)",
    "Native map tile scaling": "map.setTilesScaledToDpi(false)",
    "Manual map navigation disables aircraft follow": "followAircraft = false",
    "Aircraft recenter control": "centerOnAircraft",
    "Roof polygon drawing callback": "addEditorPoint(point)",
    "Map points persist in safety plan": "onMapSafetyPoints(",
}

missing = [label for label, token in required.items() if token not in source]
if missing:
    raise SystemExit("Map integration check failed: " + ", ".join(missing))

print("Interactive map integration checks passed.")
