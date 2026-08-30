#!/usr/bin/env bash
#
# Builds Evacuee-Register.apk from source.
#
# Deliberately does not use Gradle or the Android Gradle Plugin: this app has no
# library dependencies, so the SDK build tools are invoked directly. That keeps
# the build to packages available from Debian/Ubuntu plus one jar, and avoids
# needing dl.google.com at build time.
#
# Requirements (Debian/Ubuntu):
#   apt-get install -y android-sdk-platform-23 android-sdk-build-tools apksigner \
#                      openjdk-21-jdk-headless zip
#   dx: https://repo1.maven.org/maven2/com/jakewharton/android/repackaged/dalvik-dx/14.0.0_r21/dalvik-dx-14.0.0_r21.jar
#
set -euo pipefail

# javac/dx/keytool/apksigner all run locally; drop the proxy banner they echo.
unset JAVA_TOOL_OPTIONS

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$HERE"

SDK="${ANDROID_SDK:-/usr/lib/android-sdk}"
ANDROID_JAR="$SDK/platforms/android-23/android.jar"
DX_JAR="${DX_JAR:-/opt/androidtools/dx.jar}"

MIN_SDK=21          # Android 5.0 and later
TARGET_SDK=34       # required for installation on Android 14+
VERSION_CODE=1
VERSION_NAME="1.0"

OUT="build"
DIST="dist"
APK="$DIST/Evacuee-Register.apk"
KEYSTORE="${KEYSTORE:-$HERE/evacuee-register.jks}"
KEY_ALIAS="evacuee"
KEY_PASS="${KEY_PASS:-evacuee-register}"

for tool in aapt2 zipalign apksigner javac keytool zip; do
  command -v "$tool" >/dev/null || { echo "ERROR: '$tool' not found." >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "ERROR: android.jar missing at $ANDROID_JAR" >&2; exit 1; }
[ -f "$DX_JAR" ]      || { echo "ERROR: dx.jar missing at $DX_JAR" >&2; exit 1; }

rm -rf "$OUT"; mkdir -p "$OUT/compiled" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$DIST"

echo "==> [1/7] Refreshing bundled register from the source of truth"
cp ../evacuee-property-register.html assets/index.html

echo "==> [2/7] Compiling resources"
aapt2 compile --dir res -o "$OUT/compiled/res.zip"

echo "==> [3/7] Linking resources and manifest"
aapt2 link \
  -o "$OUT/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  -R "$OUT/compiled/res.zip" \
  --java "$OUT/gen" \
  -A assets \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay

echo "==> [4/7] Compiling Java"
# Target 8 bytecode: dx cannot read the invokedynamic that newer javac emits for
# lambdas and string concatenation.
find src "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac --release 8 -XDstringConcat=inline -nowarn \
      -classpath "$ANDROID_JAR" -d "$OUT/classes" "@$OUT/sources.txt"

echo "==> [5/7] Dexing"
java -cp "$DX_JAR" com.android.dx.command.Main \
     --dex --min-sdk-version="$MIN_SDK" \
     --output="$OUT/dex/classes.dex" "$OUT/classes"

echo "==> [6/7] Packaging and aligning"
( cd "$OUT/dex" && zip -q -X "../../$OUT/base.apk" classes.dex )
zipalign -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

echo "==> [7/7] Signing"
if [ ! -f "$KEYSTORE" ]; then
  echo "    Creating a new signing key at $KEYSTORE"
  echo "    KEEP THIS FILE. Updates must be signed with the same key, or the"
  echo "    new version cannot install over the old one."
  keytool -genkeypair -v \
    -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
    -dname "CN=Evacuee Property Register, OU=Office of the Deputy Custodian, L=Baramulla, ST=Jammu and Kashmir, C=IN" \
    >/dev/null 2>&1
fi

apksigner sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KEY_PASS" --key-pass "pass:$KEY_PASS" \
  --min-sdk-version "$MIN_SDK" \
  --v1-signing-enabled true --v2-signing-enabled true \
  --out "$APK" "$OUT/aligned.apk"

apksigner verify --min-sdk-version "$MIN_SDK" -v "$APK" | sed 's/^/    /'

echo
echo "Built $APK  ($(du -h "$APK" | cut -f1))"
