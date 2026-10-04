#!/usr/bin/env bash
# emulator-runner invokes each script entry separately; keep status in one shell.
set -uo pipefail
runtime_test_status=0
./gradlew :androidApp:connectedDebugAndroidTest --stacktrace \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true || runtime_test_status=$?
visual_capture_status=0
adb pull /sdcard/Android/data/org.quran.app.redesign/files/visual-acceptance \
  androidApp/build/outputs/visual-acceptance || visual_capture_status=$?
if [ "$runtime_test_status" -ne 0 ]; then
  exit "$runtime_test_status"
fi
exit "$visual_capture_status"
