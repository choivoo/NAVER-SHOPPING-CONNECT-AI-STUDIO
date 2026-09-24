#!/usr/bin/env bash
# Real-device QA for NAVER Shopping Connect AI Studio (Galaxy Z Fold first).
# Usage:  ./scripts/device_qa.sh [--release] [--allow-reinstall]
# Optional env: CLAUDE_API_KEY=... (live Claude test)  PRODUCT_URL=... (live product link test)
# Output: docs/device-qa/<timestamp>/  (results.md, screenshots, logcat, rendered MP4)
set -uo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-adb}"
PKG=com.shoppingconnect.aistudio.debug
TEST_PKG=$PKG.test
ACT=com.shoppingconnect.aistudio.MainActivity
OUT="docs/device-qa/$(date +%Y%m%d_%H%M%S)"
RELEASE=0; ALLOW_REINSTALL=0
for a in "$@"; do case $a in --release) RELEASE=1;; --allow-reinstall) ALLOW_REINSTALL=1;; esac; done
mkdir -p "$OUT"
REPORT="$OUT/SUMMARY.md"
row() { echo "| $1 | $2 | $3 |" >> "$REPORT"; echo "[$2] $1 — $3"; }
shot() { $ADB exec-out screencap -p > "$OUT/$1.png" 2>/dev/null && echo "  screenshot $1.png"; }

# ---- Phase 1: device ---------------------------------------------------------------------
if ! $ADB get-state >/dev/null 2>&1; then
  echo "REAL DEVICE NOT CONNECTED — connect the phone with USB debugging enabled and rerun."; exit 2
fi
{
  echo "# Device QA $(date -Iseconds)"; echo
  echo "| Item | Value |"; echo "|---|---|"
  for p in ro.product.manufacturer ro.product.model ro.build.version.release ro.build.version.sdk ro.product.cpu.abi; do echo "| $p | $($ADB shell getprop $p | tr -d '\r') |"; done
  echo "| wm size | $($ADB shell wm size | tr -d '\r' | tr '\n' ' ') |"
  echo "| wm density | $($ADB shell wm density | tr -d '\r' | tr '\n' ' ') |"
  echo "| device states | $($ADB shell cmd device_state print-states 2>/dev/null | tr -d '\r' | tr '\n' ' ') |"
  echo; echo "| Check | Result | Notes |"; echo "|---|---|---|"
} > "$REPORT"
cat "$REPORT"

# ---- Phase 2: build + install ------------------------------------------------------------
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest || { row "Build" FAIL "gradle failed"; exit 1; }
install() {
  local apk=$1 pkg=$2 out
  out=$($ADB install -r -t "$apk" 2>&1); echo "$out" | tail -1
  if echo "$out" | grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE"; then
    if [ $ALLOW_REINSTALL = 1 ]; then
      echo "Signature mismatch: uninstalling $pkg (its local data will be deleted, --allow-reinstall given)"
      $ADB uninstall "$pkg"; out=$($ADB install -t "$apk" 2>&1)
    else
      row "Install $pkg" FAIL "signature mismatch with installed build — rerun with --allow-reinstall to replace it (DELETES its app data)"; return 1
    fi
  fi
  echo "$out" | grep -q Success && row "Install $pkg" PASS "$(basename "$apk")" || { row "Install $pkg" FAIL "$(echo "$out" | tail -1)"; return 1; }
}
install app/build/outputs/apk/debug/app-debug.apk $PKG || exit 1
install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk $TEST_PKG || exit 1
$ADB shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null

# ---- Phase 3: launch + crash scan -------------------------------------------------------
$ADB logcat -c
START=$($ADB shell am start -W -n $PKG/$ACT | tr -d '\r')
row "Cold start" "$(echo "$START" | grep -q 'Status: ok' && echo PASS || echo FAIL)" "$(echo "$START" | grep -E 'TotalTime|WaitTime' | tr '\n' ' ')"
sleep 4; shot 01_launch

# ---- Phase 4: fold states (Samsung/AOSP device_state) -----------------------------------
STATES=$($ADB shell cmd device_state print-states 2>/dev/null | tr -d '\r')
if echo "$STATES" | grep -qi "fold\|CLOSED\|OPENED"; then
  # e.g. "DeviceState{identifier=0, name='CLOSED', ...}"
  closed=$(echo "$STATES" | tr '}' '\n' | grep -iE "CLOSED|FOLDED" | grep -viE "HALF|UNFOLDED" | grep -oE 'identifier=[0-9]+' | head -1 | cut -d= -f2)
  opened=$(echo "$STATES" | tr '}' '\n' | grep -iE "OPENED|UNFOLDED" | grep -viE "HALF" | grep -oE 'identifier=[0-9]+' | head -1 | cut -d= -f2)
  pid0=$($ADB shell pidof $PKG | tr -d '\r')
  act0=$($ADB shell dumpsys activity activities | grep -m1 "mResumedActivity\|topResumedActivity" | tr -d '\r')
  if [ -n "$closed" ]; then $ADB shell cmd device_state state "$closed"; sleep 3; shot 02_cover_home; fi
  if [ -n "$opened" ]; then $ADB shell cmd device_state state "$opened"; sleep 3; shot 03_inner_home; fi
  if [ -n "$closed" ]; then $ADB shell cmd device_state state "$closed"; sleep 3; shot 04_cover_again; fi
  $ADB shell cmd device_state state reset
  pid1=$($ADB shell pidof $PKG | tr -d '\r')
  act1=$($ADB shell dumpsys activity activities | grep -m1 "mResumedActivity\|topResumedActivity" | tr -d '\r')
  [ "$pid0" = "$pid1" ] && row "Fold↔Unfold process kept" PASS "pid $pid0" || row "Fold↔Unfold process kept" FAIL "pid $pid0 → $pid1"
  [ "$act0" = "$act1" ] && row "Fold↔Unfold no Activity restart" PASS "same ActivityRecord" || row "Fold↔Unfold no Activity restart" FAIL "$act0 → $act1"
else
  row "Fold states" "NOT TESTED" "device_state has no fold states (not a foldable or command unavailable) — fold/unfold manually"
fi

# ---- Phase 5/7: dark mode + large font -----------------------------------------------------
$ADB shell cmd uimode night yes; $ADB shell settings put system font_scale 1.3; sleep 3; shot 05_dark_largefont
$ADB shell cmd uimode night no; $ADB shell settings put system font_scale 1.0

# ---- Phase 6-14, 17-18: instrumented tests on the device ---------------------------------
ARGS=""
[ -n "${CLAUDE_API_KEY:-}" ] && ARGS="$ARGS -e claudeKey $CLAUDE_API_KEY"
[ -n "${PRODUCT_URL:-}" ] && ARGS="$ARGS -e productUrl $PRODUCT_URL"
$ADB shell am instrument -w -r $ARGS \
  -e class com.shoppingconnect.aistudio.device.DeviceMediaTest,com.shoppingconnect.aistudio.device.DeviceAppFlowTest,com.shoppingconnect.aistudio.device.DeviceNetworkTest \
  $TEST_PKG/androidx.test.runner.AndroidJUnitRunner > "$OUT/instrumentation.txt" 2>&1
grep -E "^OK|FAILURES|Tests run" "$OUT/instrumentation.txt" | tail -2
row "Instrumented device tests" "$(grep -q '^OK' "$OUT/instrumentation.txt" && echo PASS || echo FAIL)" "$(grep -E '^OK|Tests run' "$OUT/instrumentation.txt" | tail -1)"
$ADB pull /sdcard/Android/data/$PKG/files/qa "$OUT/qa" >/dev/null 2>&1 && cat "$OUT/qa/results.md" >> "$REPORT"

# ---- Phase 20: process death recovery ----------------------------------------------------
$ADB shell input keyevent KEYCODE_HOME; sleep 1; $ADB shell am kill $PKG; sleep 1
$ADB shell am start -W -n $PKG/$ACT >/dev/null; sleep 4; shot 06_after_process_death
row "Process death → relaunch" "$($ADB shell pidof $PKG >/dev/null && echo PASS || echo FAIL)" "check 06_after_process_death.png shows the demo project"

# ---- Phase 25: release (R8) smoke ----------------------------------------------------------
if [ $RELEASE = 1 ]; then
  ./gradlew -q :app:assembleRelease
  REL=app/build/outputs/apk/release/app-release.apk
  if [ ! -f "$REL" ]; then
    BT=$(ls -d "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/* | tail -1)
    KS="$HOME/.android/debug.keystore"
    "$BT/zipalign" -f -p 4 app/build/outputs/apk/release/app-release-unsigned.apk "$OUT/rel-aligned.apk"
    "$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$OUT/app-release-devsigned.apk" "$OUT/rel-aligned.apk"
    REL="$OUT/app-release-devsigned.apk"; echo "Release APK is DEV-SIGNED (debug keystore) — not for distribution"
  fi
  install "$REL" com.shoppingconnect.aistudio && {
    $ADB logcat -c
    $ADB shell am start -W -n com.shoppingconnect.aistudio/$ACT >/dev/null; sleep 6; shot 07_release_launch
    if $ADB logcat -d | grep -q "FATAL EXCEPTION"; then row "Release (R8) launch" FAIL "crash — see logcat.txt"; else row "Release (R8) launch" PASS "no FATAL within 6s"; fi
  }
fi

# ---- Phase 3/24: log scan ------------------------------------------------------------------
$ADB logcat -d -v threadtime > "$OUT/logcat.txt"
for pat in "FATAL EXCEPTION" "ANR in" "OutOfMemoryError" "SecurityException" "IllegalStateException"; do
  n=$(grep -c "$pat" "$OUT/logcat.txt"); [ "$n" = 0 ] && row "Logcat: $pat" PASS "0" || row "Logcat: $pat" FAIL "$n occurrence(s)"
done
leaks=$(grep -E "sk-ant-[A-Za-z0-9]|Bearer [A-Za-z0-9._-]{12,}|client_secret=[^*&[:space:]]|access_token=[^*&[:space:]]" "$OUT/logcat.txt" | grep -v '\*\*\*' | wc -l)
[ "$leaks" = 0 ] && row "Secrets in logcat" PASS "none found" || row "Secrets in logcat" FAIL "$leaks line(s) — see logcat.txt"
grep -iE "c2\.|OMX\." "$OUT/logcat.txt" | grep -i "enc" | head -5 > "$OUT/mediacodec.txt"

echo; echo "Report: $REPORT"
