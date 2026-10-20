"""Guard automatic release policy separately from optional strict source audits.

These are structural workflow regressions, not a YAML parser or runner emulator;
actionlint and real NDK acceptance provide complementary validation.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[3]


class WorkflowPolicyTests(unittest.TestCase):
    def test_automatic_callers_explicitly_request_static_acceptance(self):
        for name in ("android-ci.yml", "android-beta-release.yml"):
            text = (ROOT / ".github/workflows" / name).read_text()
            with self.subTest(workflow=name):
                job = text.split("  ethornell-native:\n", 1)[1].split("\n  release-beta:", 1)[0]
                self.assertIn("uses: ./.github/workflows/ethornell-native.yml", job)
                self.assertRegex(job, r"(?m)^      strict-release: false$")
                self.assertNotIn("--fetch-source", text)
                self.assertNotIn("--source-root", text)

    def test_release_checks_every_apk_statically_and_keeps_alignment(self):
        text = (ROOT / ".github/workflows/android-beta-release.yml").read_text()
        loop = text.split('for apk in "${apks[@]}"; do', 1)[1].split("\n          done", 1)[0]
        self.assertRegex(loop, r"verify_provenance\.py \\\n\s+--local-static \\")
        for argument in ('--ndk "$ANDROID_HOME/ndk/28.0.13004108"',
                         '--host-tag linux-x86_64', '--apk "$apk"', '--report '):
            self.assertIn(argument, loop)
        self.assertIn('zipalign" -c -P 16 -v 4 "$apk"', loop)
        self.assertIn("needs: [detect-version-change, ethornell-native]", text)
        self.assertIn("No release APK for native acceptance", text)
        self.assertNotIn("continue-on-error", text)

    def test_shared_gate_preserves_static_and_manual_strict_modes(self):
        text = (ROOT / ".github/workflows/ethornell-native.yml").read_text()
        # A caller may itself be manually dispatched while explicitly asking
        # the reusable gate for static acceptance. Do not override that input.
        self.assertIn("STRICT_RELEASE: ${{ inputs.strict-release }}", text)
        self.assertNotIn("github.event_name == 'workflow_dispatch' ||", text)
        for trigger in ("workflow_call", "workflow_dispatch"):
            with self.subTest(trigger=trigger):
                block = text.split("  " + trigger + ":\n", 1)[1]
                block = re.split(r"(?m)^  [A-Za-z_]+:\s*$|^permissions:", block, maxsplit=1)[0]
                self.assertIn("      strict-release:", block)
                self.assertIn("        type: boolean", block)
                self.assertIn("        default: true", block)
        self.assertIn("source_args=(--local-static)", text)
        self.assertIn('source_args=(--fetch-source "$RUNNER_TEMP/ethornell-source")', text)
        command = re.search(r"python3 tools/ethornell/verify_provenance\.py.*?--report [^\n]+", text, re.S).group()
        self.assertIn('"${source_args[@]}"', command)
        self.assertIn('--ndk "$ANDROID_HOME/ndk/28.0.13004108"', command)
        self.assertNotIn("continue-on-error", text)


if __name__ == "__main__":
    unittest.main()
