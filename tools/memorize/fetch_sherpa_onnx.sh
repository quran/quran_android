#!/usr/bin/env bash
# Fetches the sherpa-onnx Android AAR (Apache-2.0, https://github.com/k2-fsa/sherpa-onnx) into a
# local Maven repository under third_party/maven, verifying it against the SHA-256 published with
# the release. The memorize feature's speech engine depends on it; it isn't on Maven Central.
#
#   tools/memorize/fetch_sherpa_onnx.sh
set -euo pipefail

VERSION=1.13.8
FILE=sherpa-onnx-static-link-onnxruntime-${VERSION}.aar
SHA256=b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471
URL=https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/${FILE}

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
DEST=$ROOT/third_party/maven/com/k2fsa/sherpa/onnx/sherpa-onnx/$VERSION
AAR=$DEST/sherpa-onnx-$VERSION.aar

if [ -f "$AAR" ] && echo "$SHA256  $AAR" | sha256sum -c --status -; then
  echo "sherpa-onnx $VERSION already present"
  exit 0
fi

mkdir -p "$DEST"
curl -fL --retry 5 -o "$AAR.part" "$URL"
echo "$SHA256  $AAR.part" | sha256sum -c -
mv "$AAR.part" "$AAR"
cat > "$DEST/sherpa-onnx-$VERSION.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.k2fsa.sherpa.onnx</groupId>
  <artifactId>sherpa-onnx</artifactId>
  <version>$VERSION</version>
  <packaging>aar</packaging>
  <licenses>
    <license><name>Apache-2.0</name></license>
  </licenses>
</project>
EOF
echo "sherpa-onnx $VERSION installed in $DEST"
