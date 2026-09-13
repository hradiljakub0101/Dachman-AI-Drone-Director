#!/usr/bin/env bash
set -euo pipefail
: "${APPLE_TEAM_ID:?Set APPLE_TEAM_ID from your Apple Developer account}"
: "${DJI_APP_KEY:?Set the iOS DJI_APP_KEY in the environment}"
cd "$(dirname "$0")/../iOSApp"
xcodegen generate
pod install
# Uses certificates/accounts already available to Xcode; no password or key in arguments.
xcodebuild -workspace DachmanDroneDirector.xcworkspace -scheme DachmanDroneDirector \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/DachmanDroneDirector.xcarchive \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" archive
echo "Archive ready. Export using Xcode Organizer with the intended distribution method."
