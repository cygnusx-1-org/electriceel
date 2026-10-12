#!/bin/bash

GITHUB_REPO="cygnusx-1-org/electriceel"

BUILD_GRADLE_FILENAME="smarttubetv/build.gradle"

VERSION_NAME=$(grep '^[[:space:]]*versionName[[:space:]]*"' "${BUILD_GRADLE_FILENAME}" | head -n1 | awk '{ print $2 }' | tr -d '"')

if [ -z "$VERSION_NAME" ]; then
  echo "Error: Could not find versionName in ${BUILD_GRADLE_FILENAME}"
  exit 1
fi

APK_DIR="smarttubetv/build/outputs/apk/ststable/release"
ASSEMBLE_TASK=":smarttubetv:assembleStstableRelease"

# Only these two go on the release; scripts/update-manifest.py points the in-app updater at them.
# The x86 and universal APKs built next to them, and older versions' APKs, stay behind.
APKS=(
  "${APK_DIR}/ElectricEel_stable_${VERSION_NAME}_arm64-v8a.apk"
  "${APK_DIR}/ElectricEel_stable_${VERSION_NAME}_armeabi-v7a.apk"
)

# electriceel and the sibling repos its build reads (settings.gradle), all tagged by
# scripts/release-builds.sh
RELEASE_REPOS=("." "../MediaServiceCore" "../SharedModules")

# Guard the trees before uploading anything to GitHub. The APK filenames carry no commit, so the
# only way to know they match the release is to build them from the tagged commits, unmodified.
for REPO in "${RELEASE_REPOS[@]}"; do
  if ! git -C "${REPO}" rev-parse --git-dir > /dev/null 2>&1; then
    echo "Error: ${REPO} is not a git repository."
    exit 1
  fi

  REPO_NAME=$(basename "$(git -C "${REPO}" rev-parse --show-toplevel)")

  if ! git -C "${REPO}" rev-parse -q --verify "refs/tags/${VERSION_NAME}" > /dev/null; then
    echo "Error: ${REPO_NAME} has no tag ${VERSION_NAME}. Run scripts/release-builds.sh 1 first."
    exit 1
  fi

  if [ "$(git -C "${REPO}" rev-parse HEAD)" != "$(git -C "${REPO}" rev-parse "${VERSION_NAME}^{commit}")" ]; then
    echo "Error: ${REPO_NAME} is not at tag ${VERSION_NAME}, so the APKs would not be built from the released commits."
    exit 1
  fi

  if [ -n "$(git -C "${REPO}" status --porcelain --untracked-files=no)" ]; then
    echo "Error: ${REPO_NAME} has uncommitted changes, so the APKs would not match tag ${VERSION_NAME}."
    git -C "${REPO}" status --short --untracked-files=no
    exit 1
  fi
done

CHANGELOG_OVERRIDE_FILENAME="changelog_override.txt"

RELEASE_NOTES="${1}"
# Fall back to changelog_override.txt when no release notes were passed on the command line, so a
# direct `scripts/release-github.sh` invocation still picks up this release's notes.
if [ -z "${RELEASE_NOTES}" ] && [ -f "${CHANGELOG_OVERRIDE_FILENAME}" ]; then
  RELEASE_NOTES="$(cat "${CHANGELOG_OVERRIDE_FILENAME}")"
fi

if [ -z "${RELEASE_NOTES}" ]; then
  echo "Error: release notes are empty and ${CHANGELOG_OVERRIDE_FILENAME} was not found or is empty." >&2
  exit 1
fi

echo "Release notes set to: $RELEASE_NOTES"

./gradlew "${ASSEMBLE_TASK}" || exit 1

for APK in "${APKS[@]}"; do
  if [ ! -f "${APK}" ]; then
    echo "Error: ${APK} was not built."
    exit 1
  fi
done

# --verify-tag: the tag must already be on GitHub, or gh would create it from master instead
gh release create "${VERSION_NAME}" "${APKS[@]}" \
  --repo "${GITHUB_REPO}" \
  --verify-tag \
  --title "${VERSION_NAME}" \
  --notes "${RELEASE_NOTES}"
