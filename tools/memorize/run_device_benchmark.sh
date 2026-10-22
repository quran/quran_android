#!/usr/bin/env bash
# Runs the memorize on-device tests on real phones in Firebase Test Lab (free Spark plan: 5
# physical-device runs per day), or on a USB-connected phone.
#
#   tools/memorize/run_device_benchmark.sh testlab [engine|session] [MODEL,VERSION ...]
#   tools/memorize/run_device_benchmark.sh adb [engine|session]
#
#   engine   SherpaEngineBenchmarkTest: real-time factor, load time, worst chunk, native memory at
#            1, 2 and 4 threads ("threads=N rtf=… …", logcat tag MemorizeBenchmark)
#   session  MemorizeSessionOnDeviceTest: whole memorization sessions fed different reciters,
#            non-professional and non-native readers in real time ("clip=… revealed=… …" and
#            "group=… …", logcat tag MemorizeSession)
#
# The speech model and the test audio are bundled into the test APKs (build/memorize-testlab, not in
# git) so the phones don't have to download anything. The audio is staged by stage_test_clips.py from
# the datasets in $MEMORIZE_TEST_DATA (default ~/quran-asr-lab/data), with $MEMORIZE_TEST_PYTHON (an
# environment with numpy, soundfile and pyarrow; default ~/quran-asr-lab/.venv/bin/python).
set -euo pipefail
cd "$(dirname "$0")/../.."

# the build needs the JDK CI uses (see .java-version); gcloud may live outside PATH
TOOLCHAIN="$HOME/.quran-android-toolchain"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$TOOLCHAIN/jdk27" ]; then export JAVA_HOME="$TOOLCHAIN/jdk27"; fi
GCLOUD=$(command -v gcloud || echo "$HOME/.local/opt/google-cloud-sdk/bin/gcloud")

TARGET="${1:-}"
shift || true
SUITE="engine"
if [ "${1:-}" = "engine" ] || [ "${1:-}" = "session" ]; then SUITE="$1"; shift; fi

# the same pinned files as SpeechModelSpec.DEFAULT
MODEL_BASE="https://huggingface.co/Saboorhsn/quran-stt-onnx/resolve/51ec9bcddeaf1c480bbe39064a2a3b88bc2ff224"
stage_model() {
  local dir=build/memorize-testlab
  mkdir -p "$dir"
  while read -r name sha; do
    if [ ! -f "$dir/$name" ] || ! echo "$sha  $dir/$name" | sha256sum -c --quiet - 2>/dev/null; then
      curl -fsSL -o "$dir/$name.part" "$MODEL_BASE/$name"
      echo "$sha  $dir/$name.part" | sha256sum -c --quiet -
      mv "$dir/$name.part" "$dir/$name"
    fi
  done <<'EOF'
quran-stt-int8.onnx 31755836528da336a6192121cd7bc82cb41752dddb65566fd000b89c8686da6b
tokens.txt 252c10687e442aa9291973065fae19fa39bcd681c4f5612ec496a647e20b43a1
EOF
}

stage_clips() {
  [ -f build/memorize-testlab/clips/manifest.json ] && return
  local data="${MEMORIZE_TEST_DATA:-$HOME/quran-asr-lab/data}"
  "${MEMORIZE_TEST_PYTHON:-$HOME/quran-asr-lab/.venv/bin/python}" tools/memorize/stage_test_clips.py \
    --data "$data" --husary-audio "${MEMORIZE_HUSARY_AUDIO:-$data/../audio/husary_murattal}" \
    --text-lexicon "${MEMORIZE_TEXT_LEXICON:-$data/../cache/lexicon_v1.db}"
}

tools/memorize/fetch_sherpa_onnx.sh
stage_model
stage_clips
if [ "$SUITE" = "engine" ]; then
  ./gradlew :feature:memorize:engine-sherpa:assembleReleaseAndroidTest
  APK=feature/memorize/engine-sherpa/build/outputs/apk/androidTest/release/engine-sherpa-release-androidTest.apk
  PACKAGE=com.quran.mobile.feature.memorize.engine.sherpa.test
  TAG=MemorizeBenchmark
else
  ./gradlew :feature:memorize:assembleReleaseAndroidTest
  APK=feature/memorize/build/outputs/apk/androidTest/release/memorize-release-androidTest.apk
  PACKAGE=com.quran.mobile.feature.memorize.test
  TAG=MemorizeSession
fi

# The test APK instruments itself (it's a library's test), but Test Lab wants an app to install,
# with some code in it: a placeholder with one empty class.
host_apk() {
  local sdk build_tools platform out=build/memorize-testlab-host
  sdk=$(sed -n 's/^sdk.dir=//p' local.properties)
  build_tools=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)
  platform=$(ls -d "$sdk"/platforms/*/ | sort -V | tail -1)
  mkdir -p "$out/src/host" "$out/classes" "$out/dex"
  cat > "$out/AndroidManifest.xml" <<'EOF'
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.quran.mobile.feature.memorize.testlabhost">
  <application android:label="Memorize test host" />
</manifest>
EOF
  echo 'package host; public final class Host {}' > "$out/src/host/Host.java"
  "${JAVA_HOME:+$JAVA_HOME/bin/}javac" --release 11 -d "$out/classes" "$out/src/host/Host.java"
  "$build_tools/d8" --min-api 24 --output "$out/dex" "$out/classes/host/Host.class"
  "$build_tools/aapt2" link -o "$out/host-unsigned.apk" -I "$platform/android.jar" \
    --manifest "$out/AndroidManifest.xml" --min-sdk-version 24 --target-sdk-version 35
  (cd "$out/dex" && zip -q ../host-unsigned.apk classes.dex)
  "$build_tools/zipalign" -f 4 "$out/host-unsigned.apk" "$out/host-aligned.apk"
  "$build_tools/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
    --out "$out/host.apk" "$out/host-aligned.apk"
  echo "$out/host.apk"
}

case "$TARGET" in
  testlab)
    # low end (Galaxy A10), mid range (A54) and the Mi 11's Snapdragon 888 (Galaxy S21) by default;
    # list devices with: gcloud firebase test android models list
    if [ $# -gt 0 ]; then DEVICES=("$@"); else DEVICES=(a10,29 a54x,34 o1q,34); fi
    ARGS=()
    for d in "${DEVICES[@]}"; do
      ARGS+=(--device "model=${d%%,*},version=${d##*,}")
    done
    HOST=$(host_apk | tail -1)
    "$GCLOUD" firebase test android run --type instrumentation --app "$HOST" --test "$APK" \
      --test-runner-class androidx.test.runner.AndroidJUnitRunner \
      "${ARGS[@]}" --timeout 20m --no-use-orchestrator --results-history-name "memorize-$SUITE"
    ;;
  adb)
    adb install -r -t "$APK"
    adb shell am instrument -w -r "$PACKAGE/androidx.test.runner.AndroidJUnitRunner"
    adb logcat -d -s "$TAG"
    ;;
  *)
    echo "usage: $0 testlab [engine|session] [MODEL,VERSION ...] | adb [engine|session]" >&2
    exit 2
    ;;
esac
