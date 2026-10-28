#!/usr/bin/env bash
# Se ejecuta dentro del emulador (workflow emulador.yml). Instala la app, simula GPS y corre:
# 1) capturas de pantalla, 2) grabación en segundo plano y persistencia.
set -u
PKG=com.cumbre.app
OUT=emulador
mkdir -p "$OUT/capturas"
APK=app/build/outputs/apk/debug/app-debug.apk
TEST_APK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb install -r -g "$APK"
adb install -r -g "$TEST_APK"
adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell settings put secure location_mode 3 || true
adb shell cmd location set-location-enabled true || true

# Mapa y relieve reales para que las capturas se vean como en el teléfono.
adb shell mkdir -p /sdcard/Android/data/$PKG/files
for f in datos/argentina.map datos/relieve-cordoba.zip; do
  [ -f "$f" ] && adb push "$f" /sdcard/Android/data/$PKG/files/ >/dev/null && echo "Copiado $f"
done

python3 scripts/emulador-gps.py &
GPS=$!
sleep 6

run() {
  adb shell am instrument -w -e class "$1" $PKG.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | tee -a "$OUT/resultado.txt"
}

: > "$OUT/resultado.txt"
echo "== Capturas ==" | tee -a "$OUT/resultado.txt"
run com.cumbre.app.ScreenshotsTest
echo "== Grabación en segundo plano y persistencia ==" | tee -a "$OUT/resultado.txt"
run com.cumbre.app.RecordingBackgroundTest
run com.cumbre.app.PersistenceTest

adb pull /sdcard/Android/data/$PKG/files/capturas "$OUT/" || true
adb logcat -d -s CUMBRE_TEST:* System.out:* AndroidRuntime:* > "$OUT/logcat.txt" || true
kill $GPS || true

# am instrument no devuelve error si fallan pruebas: se revisa la salida.
if grep -qE "FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed" "$OUT/resultado.txt"; then
  echo "Hubo pruebas fallidas"
  exit 1
fi
exit 0
