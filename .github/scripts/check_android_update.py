#!/usr/bin/env python3
"""Fails the build when a new APK could not be installed as an update.

Android installs an APK over an existing CamGrid only if the package name is the same, it is
signed with the same key, and its versionCode is not lower. Any of these breaking means every
user has to uninstall (and lose their settings), so CI checks them before publishing:

  check_android_update.py --expected-cert app/signing-cert.sha256 \
      --apk dist/CamGrid-Android.apk [--previous published.apk ...]

Uses apksigner and aapt2 from the newest Android SDK build-tools ($ANDROID_HOME).
"""
import argparse
import glob
import os
import re
import subprocess
import sys
from dataclasses import dataclass


@dataclass
class ApkInfo:
    name: str
    package: str
    version_code: int
    certs: list  # SHA-256 digests of the signer certificates, lowercase hex


def normalize(digest):
    return digest.replace(":", "").strip().lower()


def parse_apksigner(output):
    """Signer certificate SHA-256 digests from `apksigner verify --print-certs`."""
    return [normalize(d) for d in re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F:]+)$", output, re.M)]


def parse_badging(output):
    """(package, versionCode) from `aapt2 dump badging`."""
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", output, re.M)
    if not match:
        raise ValueError("no package line in aapt2 output")
    return match.group(1), int(match.group(2))


def check(new, expected_cert, previous):
    """Reasons why `new` cannot update an install of the stable key or of any `previous` APK."""
    expected = normalize(expected_cert)
    errors = []
    if new.certs != [expected]:
        errors.append(f"{new.name} is signed with {new.certs or 'no certificate'}, expected [{expected}]. "
                      "Installed apps would refuse it as an update.")
    for old in previous:
        if old.package != new.package:
            errors.append(f"{new.name} has package {new.package}, {old.name} has {old.package}. "
                          "Android would install it as a second app.")
        if old.certs != [expected]:
            errors.append(f"{old.name} is signed with {old.certs}, not the expected key [{expected}]. "
                          "Either the expected fingerprint or the signing secrets are wrong.")
        if new.version_code < old.version_code:
            errors.append(f"{new.name} has versionCode {new.version_code}, lower than {old.name} "
                          f"({old.version_code}). Android would refuse it as a downgrade.")
    return errors


def build_tool(name):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        sys.exit("ANDROID_HOME is not set")
    dirs = sorted(glob.glob(os.path.join(sdk, "build-tools", "*")),
                  key=lambda d: [int(p) if p.isdigit() else 0 for p in re.split(r"[.-]", os.path.basename(d))])
    for d in reversed(dirs):
        if os.path.exists(os.path.join(d, name)):
            return os.path.join(d, name)
    sys.exit(f"{name} not found in {sdk}/build-tools")


def read_apk(path):
    signer = subprocess.run([build_tool("apksigner"), "verify", "--print-certs", path],
                            capture_output=True, text=True)
    certs = parse_apksigner(signer.stdout) if signer.returncode == 0 else []
    badging = subprocess.run([build_tool("aapt2"), "dump", "badging", path],
                             capture_output=True, text=True, check=True)
    package, code = parse_badging(badging.stdout)
    return ApkInfo(os.path.basename(path), package, code, certs)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--expected-cert", required=True, help="file with the signing certificate's SHA-256")
    parser.add_argument("--apk", required=True, action="append", help="new APK (repeatable)")
    parser.add_argument("--previous", action="append", default=[], help="published APK it must update")
    args = parser.parse_args()
    with open(args.expected_cert) as f:
        expected = next(line for line in f if line.strip() and not line.startswith("#"))
    previous = [read_apk(p) for p in args.previous]
    errors = []
    for path in args.apk:
        new = read_apk(path)
        print(f"{new.name}: {new.package} versionCode {new.version_code}, cert {new.certs}")
        errors += check(new, expected, previous)
    for old in previous:
        print(f"{old.name}: {old.package} versionCode {old.version_code}, cert {old.certs}")
    for e in errors:
        print(f"::error title=APK cannot update installed CamGrid::{e}")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
