#!/usr/bin/env bash
# Build a release APK signed with the fixed key and (optionally) upload it to the update server.
# Same flow and same key as rokid-conversate's glasses/scripts/release.sh.
#
#   scripts/release.sh <versionName X.Y.Z> [--upload]
#
# versionCode is derived from the name: X*10000 + Y*100 + Z (0.2.1 -> 201).
#
# Environment (values never go into the repository):
#   RELEASE_KEYSTORE        the rokid-conversate release keystore (alias "conversate")
#   RELEASE_KEYSTORE_PASS   its password
#   GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET   Google sign-in (or googleClientId / googleClientSecret
#                           in local.properties); without them the account tabs can't sign in
#   OTA_UPLOAD_TOKEN        for --upload (Railway variable of the rokid-conversate server)
#   UPDATE_URL              default https://server-production-24bc.up.railway.app/apps/rokid-youtube
#
# Losing the keystore means the glasses can no longer be updated over the air
# (uninstall and install again over adb).
set -euo pipefail
cd "$(dirname "$0")/.."
NAME="${1:?versionName X.Y.Z, larger than the installed one}"
UPLOAD="${2:-}"
URL="${UPDATE_URL:-https://server-production-24bc.up.railway.app/apps/rokid-youtube}"
KS="${RELEASE_KEYSTORE:?RELEASE_KEYSTORE (path) required}"
PASS="${RELEASE_KEYSTORE_PASS:?RELEASE_KEYSTORE_PASS required}"
[ -s "$KS" ] || { echo "keystore not found: $KS"; exit 1; }
if ! grep -q '^googleClientId=' local.properties 2>/dev/null && [ -z "${GOOGLE_CLIENT_ID:-}" ]; then
  echo "warning: no Google client id; the 구독 / 좋아요 tabs won't be able to sign in" >&2
fi

./gradlew --no-daemon -q :app:assembleRelease -PappVersion="$NAME" \
  -PRELEASE_KEYSTORE="$KS" -PRELEASE_KEYSTORE_PASS="$PASS" -PupdateUrl="$URL"

APK=app/build/outputs/apk/release/app-release.apk
[ -s "$APK" ] || { echo "build failed"; exit 1; }
OUT="build/rokid-youtube-$NAME.apk"
mkdir -p build
cp "$APK" "$OUT"
CODE=$(echo "$NAME" | awk -F. '{ print $1 * 10000 + $2 * 100 + $3 }')
echo "built $OUT (versionCode $CODE, $(wc -c < "$OUT") bytes)"

if [ "$UPLOAD" = "--upload" ]; then
  curl -sS --fail-with-body -X PUT "$URL/upload?versionCode=$CODE&versionName=$NAME" \
    -H "Authorization: Bearer ${OTA_UPLOAD_TOKEN:?OTA_UPLOAD_TOKEN required for --upload}" \
    -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$OUT"
  echo
fi
