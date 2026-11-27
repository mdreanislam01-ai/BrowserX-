#!/usr/bin/env bash
# Run the built Android test APK against its matching app, not an older store install.
set -euo pipefail
adb install apk/*.apk
adb install test/*.apk
runner=$(adb shell pm list instrumentation | tr -d '\r' | sed -n 's/^instrumentation:\(com\.browserx\.app[^ ]*\/androidx\.test\.runner\.AndroidJUnitRunner\).*/\1/p' | head -1)
if [[ -z "$runner" ]]; then echo '::error::BrowserX test runner was not installed'; exit 1; fi
pkg=${runner%.test/*}
adb logcat -c
adb shell am instrument -w "$runner" | tr -d '\r' | tee device.log
adb pull "/sdcard/Android/data/$pkg/files/smoke" screenshots || true
if ! grep -Eq '^OK \([1-9][0-9]* tests?\)' device.log; then
  adb logcat -d -s AndroidRuntime:E > crash.log
  summary=$(grep -E 'Error|Exception|Assertion|Reason:|Caused by|shortMsg|com.browserx.app.|failed|FAILURES|stack=' device.log crash.log | head -c 3200 || true)
  echo "::error::Android smoke test: $(tr '\n' ' ' <<< "${summary:-No successful tests; inspect device.log}")"
  exit 1
fi
