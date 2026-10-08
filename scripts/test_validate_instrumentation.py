import unittest
import xml.etree.ElementTree as ET

from validate_instrumentation import validate


class InstrumentationReportTests(unittest.TestCase):
    def report(self, count=1, failures=0, errors=0, skipped=0, child=""):
        return (f'<testsuite tests="{count}" failures="{failures}" errors="{errors}" skipped="{skipped}">'
                f'<testcase classname="Tests" name="case">{child}</testcase></testsuite>')

    def test_accepts_only_matching_successful_runner_and_report(self):
        self.assertEqual(1, validate(self.report(), "Tests:.\nOK (1 test)\n"))

    def test_rejects_mismatched_runner_or_missing_summary(self):
        for log in ("", "OK (2 tests)\n", "OK (1 test)\nOK (1 test)\n"):
            with self.assertRaises(ValueError):
                validate(self.report(), log)

    def test_rejects_failure_errors_skips_and_zero_or_inconsistent_count(self):
        for report in (self.report(failures=1), self.report(errors=1), self.report(skipped=1),
                       self.report(count=0), self.report(count=2)):
            with self.assertRaises(ValueError):
                validate(report, "OK (1 test)\n")

    def test_rejects_failed_children_even_when_summary_is_wrong(self):
        for child in ("<failure/>", "<error/>", "<skipped/>"):
            with self.assertRaises(ValueError):
                validate(self.report(child=child), "OK (1 test)\n")

    def test_rejects_runner_failure_even_with_a_success_line(self):
        with self.assertRaises(ValueError):
            validate(self.report(), "INSTRUMENTATION_FAILED\nOK (1 test)\n")

    def test_rejects_malformed_xml(self):
        with self.assertRaises(ET.ParseError):
            validate("not xml", "OK (1 test)\n")
