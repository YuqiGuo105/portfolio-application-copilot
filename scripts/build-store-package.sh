#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(node -p "require('${ROOT}/extension/manifest.json').version")"
STAGE="${ROOT}/dist/chrome-extension"
ARCHIVE="${ROOT}/dist/yuqi-application-copilot-${VERSION}.zip"

rm -rf "${STAGE}"
mkdir -p "${STAGE}" "${ROOT}/dist"

cp "${ROOT}/extension/manifest.json" "${STAGE}/"
cp -R "${ROOT}/extension/content" "${STAGE}/content"
cp -R "${ROOT}/extension/icons" "${STAGE}/icons"
cp -R "${ROOT}/extension/options" "${STAGE}/options"
cp -R "${ROOT}/extension/popup" "${STAGE}/popup"
cp -R "${ROOT}/extension/shared" "${STAGE}/shared"

rm -f "${ARCHIVE}"
(
  cd "${STAGE}"
  zip -X -q -r "${ARCHIVE}" .
)

echo "Created ${ARCHIVE}"
