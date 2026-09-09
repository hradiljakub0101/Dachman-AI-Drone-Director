#!/usr/bin/env bash
set -euo pipefail

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "Xcode is required. Install Xcode 26 or later first." >&2; exit 1
fi
xcodebuild -version
if ! command -v brew >/dev/null 2>&1; then
  echo "Homebrew is required for this bootstrap script: https://brew.sh" >&2; exit 1
fi
brew list xcodegen >/dev/null 2>&1 || brew install xcodegen
brew list cocoapods >/dev/null 2>&1 || brew install cocoapods

cd "$(dirname "$0")/../iOSApp"
xcodegen generate
pod install --repo-update

echo "Ready. Open iOSApp/DachmanDroneDirector.xcworkspace in Xcode."
