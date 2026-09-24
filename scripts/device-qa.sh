#!/usr/bin/env bash
# Device QA for NAVER Shopping Connect AI Studio — runs against whatever `adb` sees: a CI emulator
# or a real phone (e.g. Galaxy Z Fold over USB/wireless debugging). Nothing is simulated: every
# result comes from the connected device, and the report names the device it ran on.
#
#   scripts/device-qa.sh <debug.apk> <androidTest.apk> [release.apk] [out-dir]
#
# Steps: device info → install → cold start ×3 → instrumented media QA (TTS, MediaCodec render,
# MediaStore, share, cards) → instrumented UI walkthrough (demo pipeline, editor, fold/unfold,
# render, gallery, sharesheet) → process death + restore check → dark mode / large font screenshots
# → release (R8) APK smoke: install, launch, demo pipeline via UI automation → logcat scan.
set -uo pipefail

DEBUG_APK=${1:?debug apk}; TEST_APK=${2:?androidTest apk}; RELEASE_APK=${3:-}; OUT=${4:-device-qa-out}
PKG=com.shoppingconnect.aistudio.debug
REL_PKG=com.shoppingconnect.aistudio
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
mkdir -p "$OUT/screens"
SUMMARY="$OUT/summary.tsv"; : > "$SUMMARY"
log() { echo "[device-qa] $*"; }
result() { printf '%s\t%s\t%s\n' "$1" "$2" "${3:-}" >> "$SUMMARY"; log "$1: $2 ${3:-}"; }
sh_() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

adb wait-for-device
# ---------------------------------------------------------------- 1. device
{
  echo "manufacturer=$(sh_ getprop ro.product.manufacturer)"
  echo "model=$(sh_ getprop ro.product.model)"
  echo "device=$(sh_ getprop ro.product.device)"
  echo "android=$(sh_ getprop ro.build.version.release)"
  echo "sdk=$(sh_ getprop ro.build.version.sdk)"
  echo "abi=$(sh_ getprop ro.product.cpu.abi)"
  echo "emulator=$(sh_ getprop ro.kernel.qemu)"
  echo "wm_size=$(sh_ wm size | tr '\n' ' ')"
  echo "wm_density=$(sh_ wm density | tr '\n' ' ')"
  echo "device_states=$(sh_ cmd device_state print-states | tr '\n' ' ')"
} > "$OUT/device.txt"
cat "$OUT/device.txt"
if [ "$(sh_ getprop ro.kernel.qemu)" = "1" ]; then KIND="EMULATOR"; else KIND="REAL DEVICE"; fi
result "Device" "$KIND" "$(sh_ getprop ro.product.manufacturer) $(sh_ getprop ro.product.model) / Android $(sh_ getprop ro.build.version.release) (SDK $(sh_ getprop ro.build.version.sdk))"

# ---------------------------------------------------------------- 2. install
adb logcat -c
adb logcat -v threadtime > "$OUT/logcat.txt" 2>&1 & LOGCAT_PID=$!
adb uninstall "$PKG" >/dev/null 2>&1; adb uninstall "$PKG.test" >/dev/null 2>&1
if adb install -r -g "$DEBUG_APK" > "$OUT/install.txt" 2>&1 && adb install -r -g "$TEST_APK" >> "$OUT/install.txt" 2>&1; then
  result "Install (debug)" PASS "$(basename "$DEBUG_APK")"
else
  result "Install (debug)" FAIL "$(tail -1 "$OUT/install.txt")"; kill $LOGCAT_PID; exit 1
fi
[ "$(sh_ getprop ro.build.version.sdk)" -ge 33 ] && sh_ pm grant "$PKG" android.permission.POST_NOTIFICATIONS

# ---------------------------------------------------------------- 3. cold start
for i in 1 2 3; do
  sh_ am force-stop "$PKG"; sleep 1
  t=$(sh_ am start -W -n "$PKG/com.shoppingconnect.aistudio.MainActivity" | awk -F': ' '/TotalTime/{print $2}')
  echo "$t" >> "$OUT/cold_start_ms.txt"; sleep 3
done
sleep 2
if sh_ pidof "$PKG" >/dev/null; then result "Launch + cold start" PASS "TotalTime ms: $(tr '\n' ' ' < "$OUT/cold_start_ms.txt")"; else result "Launch + cold start" FAIL "process not running after launch"; fi
sh_ am force-stop "$PKG"

# ---------------------------------------------------------------- 4/5. instrumented QA
instrument() { # name class [extra args]
  local name=$1 cls=$2; shift 2
  adb shell am instrument -w -r "$@" -e class "$cls" "$RUNNER" > "$OUT/instr_$name.txt" 2>&1
  if grep -q "^OK (" "$OUT/instr_$name.txt"; then result "$name" PASS "$(grep '^OK (' "$OUT/instr_$name.txt")"
  elif grep -q "OK (0 tests)\|AssumptionViolated" "$OUT/instr_$name.txt"; then result "$name" "NOT TESTED" "skipped"
  else result "$name" FAIL "$(grep -m3 -E 'FAILURES|Tests run|INSTRUMENTATION_|Error|Exception' "$OUT/instr_$name.txt" | tr '\n' ' ' | cut -c1-300)"; fi
}
instrument media com.shoppingconnect.aistudio.qa.DeviceMediaQaTest
instrument ui com.shoppingconnect.aistudio.qa.DeviceUiQaTest

# ---------------------------------------------------------------- 6. process death
sh_ am start -W -n "$PKG/com.shoppingconnect.aistudio.MainActivity" >/dev/null; sleep 3
sh_ input keyevent KEYCODE_HOME; sleep 2
sh_ am kill "$PKG"; sleep 2
if sh_ pidof "$PKG" >/dev/null; then result "Process kill" FAIL "process still alive"; else result "Process kill" PASS "am kill while backgrounded"; fi
instrument restore com.shoppingconnect.aistudio.qa.DeviceRestoreQaTest -e restoreCheck 1

# ---------------------------------------------------------------- 7. dark mode + large font
sh_ am start -W -n "$PKG/com.shoppingconnect.aistudio.MainActivity" >/dev/null; sleep 3
sh_ cmd uimode night yes; sleep 3; adb exec-out screencap -p > "$OUT/screens/dark_home.png"
sh_ settings put system font_scale 1.3; sleep 3; adb exec-out screencap -p > "$OUT/screens/dark_largefont_home.png"
sh_ settings put system font_scale 1.0; sh_ cmd uimode night no; sleep 2
if sh_ pidof "$PKG" >/dev/null; then result "Dark mode + font 1.3x" PASS "no crash; screenshots saved"; else result "Dark mode + font 1.3x" FAIL "app died"; fi

# ---------------------------------------------------------------- pull evidence
adb pull "/sdcard/Android/data/$PKG/files/qa" "$OUT/qa" >/dev/null 2>&1 || true

# ---------------------------------------------------------------- 8. release (R8) smoke
tap_text() { # tap the first node whose text or content-desc contains $1; returns 1 if absent
  sh_ uiautomator dump /sdcard/ui.xml >/dev/null; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
  python3 - "$1" "$OUT/ui.xml" <<'PY' | { read -r x y || exit 1; adb shell input tap "$x" "$y"; }
import re, sys, xml.etree.ElementTree as ET
t = sys.argv[1]
for n in ET.parse(sys.argv[2]).iter('node'):
    if t in (n.get('text') or '') or t in (n.get('content-desc') or ''):
        a, b, c, d = map(int, re.findall(r'\d+', n.get('bounds'))); print((a + c) // 2, (b + d) // 2); break
PY
}
screen_has() { sh_ uiautomator dump /sdcard/ui.xml >/dev/null; sh_ cat /sdcard/ui.xml | grep -q "$1"; }
if [ -n "$RELEASE_APK" ]; then
  adb uninstall "$REL_PKG" >/dev/null 2>&1
  if adb install -r "$RELEASE_APK" >> "$OUT/install.txt" 2>&1; then
    [ "$(sh_ getprop ro.build.version.sdk)" -ge 33 ] && sh_ pm grant "$REL_PKG" android.permission.POST_NOTIFICATIONS
    sh_ am start -W -n "$REL_PKG/com.shoppingconnect.aistudio.MainActivity" > "$OUT/release_start.txt"; sleep 4
    for i in 1 2 3 4 5 6; do screen_has "건너뛰기" && break; screen_has "AI 콘텐츠 만들기" && break; sleep 2; done
    tap_text "건너뛰기"; sleep 2
    for i in 1 2 3 4 5; do screen_has "데모로 체험" && break; sh_ input swipe 500 1500 500 700 300; sleep 1; done
    tap_text "데모로 체험"; ok=0
    for i in $(seq 1 90); do sleep 3; screen_has "블로그 글 검수하기" && { ok=1; break; }; done
    adb exec-out screencap -p > "$OUT/screens/release_pipeline.png"
    if [ $ok = 1 ] && sh_ pidof "$REL_PKG" >/dev/null; then result "Release (R8) smoke: demo pipeline" PASS "Room/serialization/WorkManager/Hilt ran minified"
    else result "Release (R8) smoke: demo pipeline" FAIL "pipeline did not finish or app died"; fi
  else result "Release (R8) smoke" FAIL "install: $(tail -1 "$OUT/install.txt")"; fi
fi

# ---------------------------------------------------------------- 9. logcat scan
sleep 2; kill $LOGCAT_PID 2>/dev/null
APP_PIDS=$(grep -oE "Start proc [0-9]+:com\.shoppingconnect\.aistudio[^ /]*" "$OUT/logcat.txt" | awk '{print $3}' | cut -d: -f1 | sort -u | tr '\n' '|' | sed 's/|$//')
grep -nE "FATAL EXCEPTION|ANR in com\.shoppingconnect|OutOfMemoryError" "$OUT/logcat.txt" > "$OUT/logcat_crashes.txt"
if [ -n "$APP_PIDS" ]; then
  grep -E "^\S+\s+\S+\s+($APP_PIDS)\s" "$OUT/logcat.txt" > "$OUT/logcat_app.txt" || true
  grep -nE "E AndroidRuntime|SecurityException|IllegalStateException|NullPointerException" "$OUT/logcat_app.txt" > "$OUT/logcat_app_errors.txt" || true
  grep -nE "Bearer [A-Za-z0-9._-]{8,}|sk-ant-[A-Za-z0-9]|client_secret=[^*]|Authorization: [^*]|api[_-]?key=[^*]" "$OUT/logcat_app.txt" > "$OUT/logcat_secrets.txt" || true
fi
if [ -s "$OUT/logcat_crashes.txt" ]; then result "Logcat: crash/ANR/OOM" FAIL "$(head -3 "$OUT/logcat_crashes.txt" | tr '\n' ' ' | cut -c1-300)"; else result "Logcat: crash/ANR/OOM" PASS "none"; fi
if [ -s "$OUT/logcat_secrets.txt" ]; then result "Logcat: secrets" FAIL "see logcat_secrets.txt"; else result "Logcat: secrets" PASS "no tokens/keys in app logs"; fi
grep -hE "AIStudio/Render|CCodec|c2\.|OMX\." "$OUT/logcat.txt" | grep -iE "encoder|Render.*start|created component" | head -20 > "$OUT/mediacodec.txt" || true

echo; echo "==== DEVICE QA SUMMARY ($KIND) ===="; column -t -s $'\t' "$SUMMARY"
! grep -q $'\tFAIL\t' "$SUMMARY"
