#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="9.6.1"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

verify_wrapper() {
  ./gradlew --version | grep -q "Gradle ${GRADLE_VERSION}"
}

if [[ -x "./gradlew" ]]; then
  verify_wrapper
  exit 0
fi

if command -v gradle >/dev/null 2>&1; then
  gradle wrapper --gradle-version "${GRADLE_VERSION}" --distribution-type bin
else
  command -v curl >/dev/null 2>&1 || {
    echo "curl is required when system Gradle is unavailable." >&2
    exit 1
  }
  command -v unzip >/dev/null 2>&1 || {
    echo "unzip is required when system Gradle is unavailable." >&2
    exit 1
  }

  TEMP_DIR="$(mktemp -d)"
  trap 'rm -rf "${TEMP_DIR}"' EXIT

  curl --fail --location --retry 3 \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    --output "${TEMP_DIR}/gradle.zip"

  unzip -q "${TEMP_DIR}/gradle.zip" -d "${TEMP_DIR}"

  "${TEMP_DIR}/gradle-${GRADLE_VERSION}/bin/gradle" \
    wrapper \
    --gradle-version "${GRADLE_VERSION}" \
    --distribution-type bin
fi

chmod +x gradlew
verify_wrapper
