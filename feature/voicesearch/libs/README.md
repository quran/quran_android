# Sherpa-ONNX AAR

The `sherpa_onnx-release.aar` is downloaded automatically during the build by the
`downloadSherpaOnnx` Gradle task defined in `feature/voicesearch/build.gradle.kts`.

No manual steps are needed — just run `./gradlew assembleMadaniDebug` and the AAR
will be fetched and verified (SHA-256) on first build.

## Source

Built from [k2-fsa/sherpa-onnx v1.12.25](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.25),
hosted at: https://github.com/MahmoodMahmood/quran_android/releases/tag/v1.0.0-sherpa-onnx-aar
