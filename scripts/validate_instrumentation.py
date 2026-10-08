"""Require a complete, nonempty, passing Android instrumentation report."""

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def validate(report, log):
    root = ET.fromstring(report)
    cases = root.findall("testcase")
    if root.tag != "testsuite" or not cases or int(root.attrib["tests"]) != len(cases):
        raise ValueError("Instrumentation report is empty or inconsistent")
    if any(int(root.attrib[field]) != 0 for field in ("failures", "errors", "skipped")):
        raise ValueError("Instrumentation tests failed or were skipped")
    if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
        raise ValueError("Instrumentation report contains failed or skipped cases")
    summaries = re.findall(r"^OK \((\d+) tests?\)\s*$", log, re.MULTILINE)
    if summaries != [str(len(cases))]:
        raise ValueError("Runner did not confirm the complete report's test count")
    if re.search(r"FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED", log):
        raise ValueError("Runner reported an instrumentation failure")
    print(f"Verified {len(cases)} instrumented tests: no failures, errors, or skips")
    return len(cases)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--log", type=Path, required=True)
    args = parser.parse_args()
    try:
        validate(args.report.read_text(encoding="utf-8"), args.log.read_text(encoding="utf-8"))
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        print(f"Error: {error}", file=sys.stderr)
        sys.exit(1)
