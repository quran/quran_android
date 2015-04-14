quran for android
==================================

this is a simple (madani based) quran app for android.

* images from [quran images project](http://github.com/quran/quran.com-images) on github.
* translation, tafsir and Arabic data come from [tanzil](http://tanzil.net) and [King Saud University](http://quran.ksu.edu.sa/).
* audio from [Every Ayah](http://everyayah.com/).

patches, comments, etc are welcome.

contributors:

* [Hussein Maher](http://twitter.com/husseinmaher)
* [Ahmed Farra](http://github.com/afarra)
* [Shuhrat Dehkanov](http://github.com/ozbek)
* [Wael Nafee](http://twitter.com/wnafee)
* [Ahmed Fouad](http://twitter.com/fo2ad)
* [Mahmoud Hossam](http://github.com/mahmoudhossam)

graphics by [Somaia Gabr](http://twitter.com/somaiagabr).

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

* Farsi for version 2.0 by M. Jafar Nakar
* Farsi for version 1.6 by [khajavi](http://github.com/khajavi).
* Turkish by Mehmed Mahmudoglu. 
* Turkish updates by [Shuhrat Dehkanov](http://github.com/ozbek)
* Russian by Rinat (Ринат Валеев).
* Kurdish by Goran Gharib Karim.
* French by Yasser [yasserkad](http://github.com/yasserkad).
* German by [Armin Supuk](http://github.com/ArminSupuk).
* Chinese by Bo Li
* Uyghur by Abduqadir Abliz [Sahran](http://github.com/Sahran)
* Indonesian by [Saiful Khaliq](http://twitter.com/saifious)
* Malaysian by [Ahmad Syazwan](https://github.com/asyazwan)
* Spanish by [Alexander Salas](https://github.com/alexsalas)


Terms of use
------------
you are free to use parts of the Quran Android code in your application with some conditions:

* your app must be respectful of the book of Allah. adding advertisements above and below each page of Quran is unacceptable, for example (yes, someone actually did that).

* your app must provide some significant value over our app - otherwise, why not just contribute a patch instead?

* your app cannot make money from ads or from sales.  there are many reasons for this:
    - we made this app for the benefit of people and not to make a profit
    - the app costs us money (for serving the pages, translations, etc), not to mention the time to develop it and to support it.
    - not all of the data that we use is okay to be sold.  the images, along with some of the translations are on a license that allows their use and distribution for free for non-profit uses, but does not allow users to make money from them.

* if you write an application using any of the Quran data (the images, the translations, etc), you must provide a link to the respective data source page ([tanzil.net](http://tanzil.net) for the translations and the [quran images project](http://github.com/quran/quran.com-images) for the images) both within your application (in an about page) and in your application description in the market or app store.

* if you use part of (or all of) the quran android code or graphics, you must provide a link back to the [quran android project](http://github.com/ahmedre/quran_android) in your application description and your application itself in an about section.

Open Source Projects Used
-------------------------
* android-support library (support-v4 and appcompat-v7)
* [AndroidSlidingUpPanel](https://github.com/umano/AndroidSlidingUpPanel)
* [okhttp](https://github.com/square/okhttp)

Changelog
---------
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

You can see our complete set of releases [here] (https://github.com/ahmedre/quran_android/releases)
