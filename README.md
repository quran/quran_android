[![Build Status](https://travis-ci.org/quran/quran_android.svg?branch=master)](https://travis-ci.org/quran/quran_android)

# Quran for Android

[<img align="right" src="https://raw.githubusercontent.com/quran/quran_android/master/app/src/madani/res/drawable-xxxhdpi/icon.png" />](https://play.google.com/store/apps/details?id=com.quran.labs.androidquran)

this is a simple (madani based) quran app for android.

* madani images from [quran images project](https://github.com/quran/quran.com-images) on github.
* qaloon images used with permission of Nous Memes Editions Et Diffusion (Tunisia).
* naskh images used with permission of SHL Info Systems.
* translation, tafsir and Arabic data come from [tanzil](http://tanzil.net) and [King Saud University](https://quran.ksu.edu.sa).

## Contributing

If you'd like to contribute, please take a look at the [PRs Welcome](https://github.com/quran/quran_android/issues?q=is%3Aissue+is%3Aopen+label%3A%22PRs+Welcome%22) label on the issue tracker. For new features, please open an issue to discuss it before beginning implementation.

Use [`quran_android-code_style.xml`](https://github.com/quran/quran_android/blob/master/quran_android-code_style.xml) for Android Studio / IntelliJ code styles. Import it by copying it to the Android Studio/IntelliJ IDEA codestyles folder. For Android Studio, that folder is located at `~/.AndroidStudio[Version]/config/codestyles` (the root folder name may differ depending on the host machine and Android Studio version, but the rest of the path should be same). After copying the `quran_android-code_style.xml`, go to Code Style preferences screen and choose `quran_android-code_style` from Code Style Schemes.

May Allah reward all the awesome [Contributors and Translators](https://github.com/quran/quran_android/blob/master/CONTRIBUTORS.md).


## Setup

### Command Line

You can build Quran from the command line by running `./gradlew assembleMadaniDebug`.

### Android Studio / IntelliJ

Choose "Import Project," and choose the `build.gradle` file from the top level directory. Under "Build Variants" (a tab on the left side), choose "madaniDebug."

## Open Source Projects Used

* [Android Support Library](https://developer.android.com/topic/libraries/support-library/features.html)
* [AndroidSlidingUpPanel](https://github.com/umano/AndroidSlidingUpPanel)
* [OkHttp](https://github.com/square/okhttp)
* [RxJava 2](https://github.com/ReactiveX/RxJava)
* [RxAndroid](https://github.com/ReactiveX/RxAndroid)
* [moshi](https://github.com/square/moshi)
* [dagger2](http://google.github.io/dagger/)
* [retrolambda](https://github.com/orfjackal/retrolambda) and [retrolambda gradle plugin](https://github.com/evant/gradle-retrolambda).
* [butterknife](http://jakewharton.github.io/butterknife/)
