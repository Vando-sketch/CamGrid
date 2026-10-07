"""Tests for check_android_update.py. Run: python3 -m unittest discover -s .github/scripts"""
import unittest

from check_android_update import ApkInfo, check, parse_apksigner, parse_badging

STABLE = "8d7fc72803d7015894c19bf51dd7d108bc23c7b9469eea113a3d2e35d7e6888a"
OTHER = "c52cc7e2d1ca5bb1" + "0" * 48
PACKAGE = "io.github.vandosketch.camgrid"


def apk(code, cert=STABLE, package=PACKAGE, name="new.apk"):
    return ApkInfo(name, package, code, [cert])


class ParseTest(unittest.TestCase):
    def test_apksigner_certs(self):
        out = (
            "Signer #1 certificate DN: CN=CamGrid\n"
            f"Signer #1 certificate SHA-256 digest: {STABLE}\n"
            "Signer #1 certificate SHA-1 digest: 0123456789abcdef0123456789abcdef01234567\n"
            "Signer #1 certificate MD5 digest: 0123456789abcdef0123456789abcdef\n"
        )
        self.assertEqual(parse_apksigner(out), [STABLE])

    def test_apksigner_without_certs(self):
        self.assertEqual(parse_apksigner("DOES NOT VERIFY\n"), [])

    def test_badging(self):
        out = (
            f"package: name='{PACKAGE}' versionCode='81' versionName='0.2.81' "
            "platformBuildVersionName='16' platformBuildVersionCode='36'\n"
            "sdkVersion:'25'\n"
        )
        self.assertEqual(parse_badging(out), (PACKAGE, 81))


class CheckTest(unittest.TestCase):
    def test_newer_build_with_stable_key_passes(self):
        self.assertEqual(check(apk(82), STABLE, [apk(81, name="preview")]), [])

    def test_first_build_without_previous_passes(self):
        self.assertEqual(check(apk(82), STABLE, []), [])

    def test_expected_cert_is_case_and_colon_insensitive(self):
        upper = ":".join(STABLE[i:i + 2] for i in range(0, 64, 2)).upper()
        self.assertEqual(check(apk(82), upper, []), [])

    def test_wrong_key_fails(self):
        errors = check(apk(82, cert=OTHER), STABLE, [])
        self.assertEqual(len(errors), 1)
        self.assertIn("signed with", errors[0])

    def test_unsigned_fails(self):
        self.assertTrue(check(ApkInfo("new.apk", PACKAGE, 82, []), STABLE, []))

    def test_same_version_code_passes(self):
        # A re-run of the same workflow run keeps its number; Android installs an equal versionCode.
        self.assertEqual(check(apk(81), STABLE, [apk(81, name="preview")]), [])

    def test_lower_version_code_fails(self):
        # For example a run counter that started again after the workflow file was renamed.
        errors = check(apk(3), STABLE, [apk(81, name="preview")])
        self.assertEqual(len(errors), 1)
        self.assertIn("versionCode", errors[0])

    def test_package_rename_fails(self):
        errors = check(apk(82, package="io.github.other"), STABLE, [apk(81, name="preview")])
        self.assertTrue(any("package" in e for e in errors))

    def test_previous_release_with_other_key_fails(self):
        # The published build users have installed is not signed with the key we expect.
        self.assertTrue(check(apk(82), STABLE, [apk(81, cert=OTHER, name="preview")]))


if __name__ == "__main__":
    unittest.main()
