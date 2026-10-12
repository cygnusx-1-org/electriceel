#!/usr/bin/env python3
"""Writes updates/electriceel_<flavor>.json, the manifest the in-app update checker reads.

The checker (SharedModules/appupdatechecker2, AppVersionChecker.triggerFromJson) downloads it from
raw.githubusercontent.com on master (common/src/st<flavor>/res/values/update_urls.xml), then:
  - offers an update only when the newest entry's versionName is higher than the installed one; versionCode
    is only passed on, so versions may share one (the script writes build.gradle's versionCode as is);
  - downloads the APK listed for the device's primary ABI (downloadUrlList_<abi>), else downloadUrlList;
  - shows the changelog of every entry newer than the installed version, so older entries are kept.

The version comes from smarttubetv/build.gradle, the APKs from the GitHub release tagged with it.
The changelog is the first of:
  - the -m lines;
  - changelog_override.txt at the repo root: one line per entry, "- " or "* " bullets allowed, # comments and
    blank lines skipped;
  - the commit subjects since the previous version tag.

Usage:
  scripts/update-manifest.py                      # stable, changelog from the override file or git log
  scripts/update-manifest.py -m "Fixed X" -m "Added Y"
  scripts/update-manifest.py --flavor beta --dry-run
"""
import argparse
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
REPO = "cygnusx-1-org/electriceel"
# The ABIs uploaded to each release; the first 32-bit one is the fallback for every other device
ABIS = ["arm64-v8a", "armeabi-v7a"]
FALLBACK_ABI = "armeabi-v7a"
VERSION_TAG = re.compile(r"^\d+(\.\d+)*$")
CHANGELOG_OVERRIDE = os.path.join(ROOT, "changelog_override.txt")


def fail(message):
    sys.exit("error: " + message)


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, check=True, capture_output=True, text=True).stdout


def read_version():
    """versionName and versionCode of defaultConfig in smarttubetv/build.gradle"""
    with open(os.path.join(ROOT, "smarttubetv", "build.gradle"), encoding="utf-8") as fh:
        gradle = fh.read()

    name = re.search(r'^\s*versionName\s+"([^"]+)"', gradle, re.MULTILINE)
    code = re.search(r"^\s*versionCode\s+(\d+)", gradle, re.MULTILINE)
    if not name or not code:
        fail("no versionName or versionCode in smarttubetv/build.gradle")

    return name.group(1), int(code.group(1))


def version_key(version):
    return [int(part) for part in version.split(".")]


def previous_tag(version):
    """The newest version tag older than the version, or None"""
    tags = [t for t in git("tag").split() if VERSION_TAG.match(t) and version_key(t) < version_key(version)]
    return max(tags, key=version_key) if tags else None


def changelog_from_git(version):
    """Subjects of the commits since the previous version tag, up to the version's tag (or HEAD without one)"""
    end = version if version in git("tag").split() else "HEAD"
    start = previous_tag(version)
    revs = f"{start}..{end}" if start else end

    subjects = git("log", "--no-merges", "--format=%s", revs).splitlines()
    # The version bump says nothing to the user; a subject repeated by several commits is one line
    lines = [s.strip() for s in subjects if s.strip() and not re.match(r"(?i)^updated? version", s.strip())]
    return list(dict.fromkeys(lines))


def changelog_from_file(path):
    """The entries of the override file, or None when there's no file"""
    if not os.path.isfile(path):
        return None

    lines = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            lines.append(re.sub(r"^[-*]\s+", "", line))

    return lines


def apk_url(version, flavor, abi):
    return f"https://github.com/{REPO}/releases/download/{version}/ElectricEel_{flavor}_{version}_{abi}.apk"


def url_exists(url):
    request = urllib.request.Request(url, method="HEAD")
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status == 200
    except urllib.error.URLError:
        return False


def main():
    parser = argparse.ArgumentParser(description="Write updates/electriceel_<flavor>.json for the in-app update checker.")
    parser.add_argument("--flavor", choices=["stable", "beta"], default="stable")
    parser.add_argument("--version", help="versionName (default: smarttubetv/build.gradle)")
    parser.add_argument("--code", type=int, help="versionCode (default: smarttubetv/build.gradle)")
    parser.add_argument("-m", "--changelog", action="append", metavar="LINE",
                        help="a changelog line, repeatable (default: changelog_override.txt, "
                             "else the commit subjects since the previous version tag)")
    parser.add_argument("--no-check", action="store_true", help="don't check that the release has the APKs")
    parser.add_argument("--dry-run", action="store_true", help="print the manifest instead of writing it")
    args = parser.parse_args()

    gradle_version, gradle_code = read_version()
    version = args.version or gradle_version
    code = args.code if args.code is not None else gradle_code

    if not VERSION_TAG.match(version):
        fail(f"{version!r} isn't a version like 32.56.2")

    path = os.path.join(ROOT, "updates", f"electriceel_{args.flavor}.json")

    manifest = {}
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as fh:
            manifest = json.load(fh)

    older = {name: info for name, info in manifest.items() if name not in ("package", version)}

    if args.changelog:
        changelog, source = args.changelog, "-m"
    else:
        changelog, source = changelog_from_file(CHANGELOG_OVERRIDE), os.path.relpath(CHANGELOG_OVERRIDE, ROOT)
        # An empty override file is one left over from the last release: fall back to git
        if not changelog:
            changelog, source = changelog_from_git(version), "git log"

    if not changelog:
        fail(f"no changelog: {os.path.relpath(CHANGELOG_OVERRIDE, ROOT)} is empty or missing and there are "
             f"no commits since the previous version tag, pass it with -m")

    print(f"Changelog from {source}", file=sys.stderr)

    urls = {abi: apk_url(version, args.flavor, abi) for abi in ABIS}

    if not args.no_check:
        missing = [url for url in urls.values() if not url_exists(url)]
        if missing:
            fail("the release doesn't have:\n  " + "\n  ".join(missing) +
                 "\nupload the APKs first, or pass --no-check")

    package = {f"downloadUrlList_{abi}": [url] for abi, url in urls.items()}
    package["downloadUrlList"] = [urls[FALLBACK_ABI]]

    # Newest first, like the file reads
    result = {"package": package, version: {"versionCode": code, "changelog": changelog}}
    for name in sorted(older, key=lambda n: version_key(n) if VERSION_TAG.match(n) else [], reverse=True):
        result[name] = older[name]

    text = json.dumps(result, indent=2, ensure_ascii=False) + "\n"

    if args.dry_run:
        sys.stdout.write(text)
        return

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)

    print(f"Wrote {os.path.relpath(path, ROOT)}: {version} (versionCode {code}), {len(changelog)} changelog lines, "
          f"{len(older)} older versions kept", file=sys.stderr)


if __name__ == "__main__":
    main()
