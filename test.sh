#!/bin/sh
# Runs both test suites: the converter's (pytest) and the app's (JUnit/Robolectric).
set -e
cd "$(dirname "$0")"
(cd converter && uv run pytest -q)
./gradlew :app:testDebugUnitTest -q
python3 - <<'PY'
import glob, re
total = bad = 0
for path in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"', open(path).read())
    total += int(m[1]); bad += int(m[2]) + int(m[3])
print(f"app: {total} tests, {bad} failed")
raise SystemExit(1 if bad else 0)
PY
