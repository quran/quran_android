quran for android
==================================

this is a simple (madani based) quran app for android.

* madani images from [quran images project](http://github.com/quran/quran.com-images) on github.
* qaloon images used with permission of Nous Memes Editions Et Diffusion (Tunisia).
* naskh images used with permission of SHL Info Systems
* translation, tafsir and Arabic data come from [tanzil](http://tanzil.net) and [King Saud University](http://quran.ksu.edu.sa/).

patches, comments, etc are welcome.

contributors:

* [Asim Mohiuddin](https://github.com/asimmohiuddin) - images, gapless audio 
    and work on the Naskh version
* [g360230](https://github.com/g360230) - images and work on the Qaloon
    version
* [Somaia Gabr](http://twitter.com/somaiagabr) - ui and graphics.
* [Shuhrat Dehkanov](http://github.com/ozbek)
* [Ahmed Farra](http://github.com/afarra)
* [Hussein Maher](http://twitter.com/husseinmaher)
* [Wael Nafee](http://twitter.com/wnafee)
* [Ahmed Fouad](http://twitter.com/fo2ad)
* [Mahmoud Hossam](http://github.com/mahmoudhossam)
* [Rehab Mohamed](http://twitter.com/hams_rrr)
* [Ahmed Essam](http://twitter.com/neo_4583)


Code Style
------------------------
General [Android code style guidelines](https://source.android.com/source/code-style.html) apply, with the exception of following indent sizes:
- tab size: 2 space
- indent: 2 space
- continuous indent: 4 space

You can set these under Code Style in Android Studio or IntelliJ IDEA.

Alternatively, you may copy [`quran_android-code_style.xml`](https://github.com/quran/quran_android/blob/master/quran_android-code_style.xml) to Android Studio/IntelliJ IDEA codestyles folder. For Android Studio, that folder is located at `~/.AndroidStudioPreview1.2/config/codestyles` (the root folder name may differ depending on the host machine and Android Studio version, but the rest of the path should be same). After copying the `quran_android-code_style.xml`, go to Code Style preferences screen and choose `quran_android-code_style` from Code Style Schemes.

Setup
------------------------
1. get and install the [android sdk](http://developer.android.com/sdk/index.html)
2. make sure `$ANDROID_HOME` is set to the correct place.
3. build from the command line by running `./gradlew assembleDebug`
4. if you want an ide, download the latest version of [android studio](http://tools.android.com/download/studio/canary) (latest as of this writing is version 0.8.14)
5. run android studio, choose import project, then choose build.gradle from the main quran source code directory.

and that's it!

App localization
------------------------

* Farsi for version 2.0 by [M. Jafar Nakar](https://github.com/mjnanakar).
* Farsi for version 1.6 by [khajavi](http://github.com/khajavi).
* Turkish by Mehmed Mahmudoglu. 
* Turkish updates by [Shuhrat Dehkanov](http://github.com/ozbek).
* Russian by Rinat [Ринат Валеев](https://github.com/Valey).
* Kurdish by [Goran Gharib Karim](https://github.com/GorranKurd).
* French by Yasser [yasserkad](http://github.com/yasserkad).
* German by [Armin Supuk](http://github.com/ArminSupuk).
* Chinese by [Bo Li](http://twitter.com/liboat).
* Uyghur by Abduqadir Abliz [Sahran](http://github.com/Sahran).
* Indonesian by [Saiful Khaliq](http://twitter.com/saifious).
* Malaysian by [Ahmad Syazwan](https://github.com/asyazwan).
* Spanish by [Alexander Salas](https://github.com/alexsalas).
* Uzbek by [Shuhrat Dehkanov](https://github.com/ozbek").

and many others, may Allah reward everyone!

Open Source Projects Used
-------------------------
* android-support library (support-v4 and appcompat-v7)
* [AndroidSlidingUpPanel](https://github.com/umano/AndroidSlidingUpPanel)
* [OkHttp](https://github.com/square/okhttp)
* [RxJava](https://github.com/ReactiveX/RxJava)
* [RxAndroid](https://github.com/ReactiveX/RxAndroid)
* [moshi](https://github.com/square/moshi)

Changelog
---------
**current**
- import / export bookmarks
- merged tags and bookmarks into one screen
- lots of improvements and bugfixes

**version 2.6.7-p4 (released 11/15/2015)**
- fix launch crash when upgrading prefs for people stuck between the bug
  introduced in p2 (not crashing with it and instead running the wrong
  behavior), and the fix in p3.
    
**version 2.6.7-p3 (released 11/15/2015)**
- fix launch crash when upgrading prefs

**version 2.6.7-p2 (released 11/14/2015)**
- fix audio scrolling issue
- fix tashkeel being cut off on fonts
- new about screen
- improvements to preferences
- repeat bug fixes
- permissions related bug fixes

**version 2.6.7-p1 (released 10/24/2015)**
- fix audio manager launch crash due to rx proguard issue.

**version 2.6.7 (released 10/24/2015)**
- only use uthmani font on apis 18 and above.
- better handling for landscape audio highlighting.
- improve "repeat verses" wording
- increase Quran text size in translation view
- decrease line spacing for Quran text in translation view
- upgrade libraries.

_flavor specific_
- naskh: highlighting fix

**version 2.6.6 (released 10/4/2015)**
- android M support
- use different font for arabic text
- add 12 new gapless qaris
- separate basmallah from first ayah in translation
- fix buggy audio autoscrolling in landscape quran/quran_android#545
- fix "calculating app size" quran/quran_android#536
- many bugfixes and improvements

_flavor specific_
- qaloon: patch to fix page 304.
- qaloon: add a qari, sheikh Ahmad Khidr Attarabolsi
- naskh: use different database and font for arabic text

**version 2.6.5p1 (released 5/31/2015)**
- fix a crash when tapping a search result

**version 2.6.5 (released 5/31/2015)**
- improvements for rtl layouts in rtl languages on apis 17+
- update image version to fix #529 and fix pages 1 and 2
- many ui improvements (audio bar, tablet, ripples on l+, etc)
- fix arabic setting on android M
- material style dialogs
- lots of bugfixes and improvements

**version 2.6.4p1 (released 4/13/2015)**
- revert support-v4 and appcompat-v7 to v21.0.3 due to contextual action
bar bug. see https://code.google.com/p/android/issues/detail?id=165243

**version 2.6.4 (released 4/13/2015)**
- minor fixes for crashes
- since it seems that the notification crashes happen at random, and only
after lots of notification traffic, this patch attempts to cut down on
the notification traffic by remembering the last progress and maximum
values and only posting the notification if they changed.

**version 2.6.3 (released 4/12/2015)**
- fix a bug where audio bar was hidden on tablet in landscape
- persist highlight of ayah between translation and pages
- highlight the verse number along with a verse
- additional attempts to working around notification and LG crashes
- minor bugfixes and improvements

**version 2.6.2 (released 4/1/2015)**
- spinner is now wide throughout
- spinner automatically jumps to the selected value
- use material style search
- swap next and previous buttons in notification
- added Turkish sura names, shortened Russian translation of sura names
- improvements to custom storage location preference screen
- a plethora of bugfixes

**version 2.6.1 (released 3/10/2015)**
- fix audio not playing without connection
- fix some crashes and add some crash logging

**version 2.6.0 (released 3/8/2015)**
- material design!
- experimental audio manager with download all functionality.
- change the translator from the translation popup.
- tons of bugfixes.
- now only supports sdk 14+ (ice cream sandwich and above)

**version 2.5.8 (released 7/5/2014)**
- fixing tablet bugs causing the page not to show up

**version 2.5.7 (released 7/4/2014)**
- added arrows to switch to next/previous ayah from translation panel
- added translator name in panel
- more clear night mode setting (thanks @ozbek)
- show an error and retry button instead of a blank page when page can't load
- spanish translation (thanks @alexsalas)
- update turkish translation (thanks @ozbek)
- many bugfixes

**version 2.5.6 (released 6/29/2014)**
- fix a crash with panel and audio settings
- fix tags panel not updating
- update single image downloading code

**version 2.5.5 (released 6/27/2014)**
- advanced audio repeat options
- rich audio notifications
- fix tablet related issues

**version 2.5.4 (released 6/13/2014)**
- bugfixes (mainly preferences crash)
- reset toolbar when you choose a different ayah

**version 2.5.3 (released 6/11/2014)**
- fix toolbar bugs on android 2.3
- fix "current page" not being clickable
- fix lots of search crashes
- fix saving on external sdcard on kitkat
- fix tags and bookmarks not refreshing

**version 2.5.2 (released 6/7/2014)**
- major ui improvements for long press actions
- option to highlight ayah bookmarks by default
- malaysian translation (thanks @asyazwan)
- uyghur translation (thanks @Sahran)
- right to left layout for arabic (thanks @aessam)
- many bugfixes and improvements

You can see our complete set of releases [here] (https://github.com/quran/quran_android/releases)
