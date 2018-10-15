[![Build Status](https://travis-ci.org/quran/quran_android.svg?branch=master)](https://travis-ci.org/quran/quran_android)
# Quran for Android
[<img align="right" alt="Get it on Google Play" height="128" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png">](https://play.google.com/store/apps/details?id=com.quran.labs.androidquran)

This is a simple (Madani based) Quran app for Android.

* madani images from [quran images project](https://github.com/quran/quran.com-images) on github.
* qaloon images used with permission of Nous Memes Editions Et Diffusion (Tunisia).
* naskh images used with permission of SHL Info Systems.
* translation, tafsir and Arabic data come from [tanzil](http://tanzil.net) and [King Saud University](https://quran.ksu.edu.sa).

## Contributing

If you'd like to contribute, please take a look at the [PRs Welcome](https://github.com/quran/quran_android/issues?q=is%3Aissue+is%3Aopen+label%3A%22PRs+Welcome%22) label on the issue tracker. For new features, please open an issue to discuss it before beginning implementation.

Use [`quran_android-code_style.xml`](https://github.com/quran/quran_android/blob/master/quran_android-code_style.xml) for Android Studio / IntelliJ code styles. Import it by copying it to the Android Studio/IntelliJ IDEA codestyles folder. For Android Studio, that folder is located at `~/.AndroidStudio[Version]/config/codestyles` (the root folder name may differ depending on the host machine and Android Studio version, but the rest of the path should be same). After copying the `quran_android-code_style.xml`, go to Code Style preferences screen and choose `quran_android-code_style` from Code Style Schemes.

Though very rarely, we do push beta versions in Play Store for early testing. If you would like to participate in beta program, please join our [Quran for Android](https://plus.google.com/communities/100110719319613677297) community in Google+.

May Allah reward all the awesome [Contributors and Translators](https://github.com/quran/quran_android/blob/master/CONTRIBUTORS.md).


## Setup

### Command Line

You can build Quran from the command line by running `./gradlew assembleMadaniDebug`.

### Android Studio / IntelliJ

Choose "Import Project," and choose the `build.gradle` file from the top level directory. Under "Build Variants" (a tab on the left side), choose "madaniDebug."

## Using Quran for Android code in other projects

The intention behind open sourcing Quran for Android is two fold - first, to allow developers to help contribute to the app, thus speeding up the development of new features and ideas. Second, to give back to the community and serve as a code reference.

Quran for Android costs money to run - all the data (pages, audio files, and translations) are hosted on servers that people volunteer their money to pay for every month. Moreover, the data itself is the work of various scholars, organizations, or reciters, many of whom provide this data free for usage for the benefit of the ummah.

Therefore, people planning on taking this project and profiting from it (by way of ads, in app purchases, etc) are in fact stealing from the work of the contributors of this project, and from the people who volunteer to pay for the servers (since they increase the bandwidth costs on them instead of covering them themselves).

Please keep use of this code for non-profit purposes only. Also, please note that the project is under the GPL 3 license, which requires that modifications to this code be open sourced as well. Please note that the data is licensed under the various licenses of the data's authors (typically, this is [CC BY-NC-ND](https://creativecommons.org/licenses/by-nc-nd/2.0/), but may differ depending on the source of the data).


## Open Source Projects Used

* [Android Support Library](https://developer.android.com/topic/libraries/support-library/features.html)
* [AndroidSlidingUpPanel](https://github.com/umano/AndroidSlidingUpPanel)
* [OkHttp](https://github.com/square/okhttp)
* [RxJava 2](https://github.com/ReactiveX/RxJava)
* [RxAndroid](https://github.com/ReactiveX/RxAndroid)
* [moshi](https://github.com/square/moshi)
* [dagger2](http://google.github.io/dagger/)
