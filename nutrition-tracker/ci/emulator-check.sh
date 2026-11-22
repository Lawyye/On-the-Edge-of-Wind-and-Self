#!/usr/bin/env bash
# Проверка на эмуляторе (вызывается из CI):
#  1. отладочная сборка: онбординг через интерфейс и запись воды;
#  2. переустановка поверх (как обновление) и проверка, что данные на месте;
#  3. снимки экранов основных разделов;
#  4. release-сборка (с R8): установка, запуск, случайные нажатия (monkey) без падений.
set -uo pipefail

APKS="$1"
OUT="${2:-/tmp/nt-shots}"
mkdir -p "$OUT"
PKG_DEBUG=com.nutritiontracker.app.debug
PKG_TEST=com.nutritiontracker.app.debug.test
PKG_REL=com.nutritiontracker.app
RUNNER="$PKG_TEST/androidx.test.runner.AndroidJUnitRunner"
FAIL=0

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 1; done'
adb shell input keyevent 82 || true
# Системные диалоги эмулятора («Launcher isn't responding») не должны закрывать снимки.
adb shell settings put global hide_error_dialogs 1 || true

run_test() {
  local out
  out=$(adb shell am instrument -w -e class "$1" "$RUNNER" 2>&1)
  echo "$out"
  if ! echo "$out" | grep -q "OK ("; then
    echo "::error::Инструментальный тест $1 не прошёл"
    adb logcat -d | grep -E "FATAL EXCEPTION|AndroidRuntime" -A25 | tail -80
    FAIL=1
  fi
}

shot() {
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  sleep 2
  adb exec-out screencap -p > "$OUT/$1.png"
}

echo "== Отладочная сборка: онбординг через интерфейс"
adb install -r "$APKS/debug/app-debug.apk"
adb install -r "$APKS/androidTest/debug/app-debug-androidTest.apk"
run_test com.nutritiontracker.OnboardingFlowTest

echo "== Переустановка поверх и проверка данных"
adb install -r "$APKS/debug/app-debug.apk"
run_test com.nutritiontracker.DataSurvivesReinstallTest

echo "== Снимки экранов"
adb shell am start -W -n "$PKG_DEBUG/com.nutritiontracker.MainActivity"
shot 1-home
SIZE=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}
Y=$((H - H * 55 / 1000))
for i in 1 2 3 4; do
  X=$((W * (2 * i + 1) / 10))
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
  adb shell input tap "$X" "$Y"
  shot "$((i + 1))-tab"
done
adb shell am force-stop "$PKG_DEBUG"

echo "== Release-сборка: запуск и случайные нажатия"
adb install -r "$APKS/release/app-release.apk"
adb logcat -c
adb shell am start -W -n "$PKG_REL/com.nutritiontracker.MainActivity"
shot 6-release-start
if [ -z "$(adb shell pidof "$PKG_REL" | tr -d '\r')" ]; then
  echo "::error::Release-приложение не запустилось"
  adb logcat -d | tail -200
  FAIL=1
fi
adb shell monkey -p "$PKG_REL" --pct-syskeys 0 --pct-appswitch 0 --throttle 120 -s 42 -v 600 > "$OUT/monkey.txt" 2>&1 || true
if grep -qE "// CRASH|// NOT RESPONDING" "$OUT/monkey.txt"; then
  echo "::error::Release-приложение упало при случайных нажатиях"
  grep -A40 -E "// CRASH|// NOT RESPONDING" "$OUT/monkey.txt" | head -80
  FAIL=1
fi
CRASHES=$(adb logcat -d | grep -A40 "FATAL EXCEPTION" | grep -B5 -A35 "Process: $PKG_REL" || true)
if [ -n "$CRASHES" ]; then
  echo "::error::FATAL EXCEPTION в журнале release-приложения"
  echo "$CRASHES" | head -80
  FAIL=1
fi
tail -5 "$OUT/monkey.txt"

exit $FAIL
