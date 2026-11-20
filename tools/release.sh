#!/usr/bin/env bash
# Publish a new version that installed apps will offer as an in-app update.
#   1. Bump appVersionCode + appVersionName in app/build.gradle.kts
#   2. ./tools/release.sh "What changed (shown in the release notes)"
set -euo pipefail
cd "$(dirname "$0")/.."

NOTES="${1:-Improvements and fixes}"
VERSION=$(sed -n 's/^val appVersionName = "\(.*\)"/\1/p' app/build.gradle.kts)
REPO=$(sed -n 's/^val updateRepo = "\(.*\)"/\1/p' app/build.gradle.kts)
[ -f keystore.properties ] || { echo "keystore.properties missing: releases must use the release key" >&2; exit 1; }
if gh release view "v$VERSION" --repo "$REPO" >/dev/null 2>&1; then
  echo "v$VERSION already exists on $REPO: bump appVersionName/appVersionCode first" >&2; exit 1
fi

./gradlew :app:testDebugUnitTest :app:assembleRelease
OUT=app/build/outputs/apk/release
mkdir -p build/release
for abi in arm64-v8a armeabi-v7a; do
  cp "$OUT/app-$abi-release.apk" "build/release/Bayyinah-$VERSION-$abi-release.apk"
done

git tag -f "v$VERSION"
git push origin "v$VERSION" --force
gh release create "v$VERSION" build/release/Bayyinah-"$VERSION"-*.apk \
  --repo "$REPO" --title "Bayyinah $VERSION" \
  --notes "$NOTES

**Install / update:** most phones → \`arm64-v8a\`; older 32-bit phones → \`armeabi-v7a\`.
Installed copies of Bayyinah offer this update automatically."
echo "Published v$VERSION"
