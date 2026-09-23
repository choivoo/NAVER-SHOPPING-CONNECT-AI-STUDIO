#!/usr/bin/env bash
# Builds release APK + AAB (R8 minified). Signed only if keystore.properties exists.
set -euo pipefail
cd "$(dirname "$0")"
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease :app:bundleRelease "$@"
ls -la app/build/outputs/apk/release/ app/build/outputs/bundle/release/
if [ ! -f keystore.properties ]; then
  echo "NOTE: keystore.properties not found -> app-release-unsigned.apk (sign it before installing; see SETUP_GUIDE.md)."
fi
