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
* [Wael Nafee](http://twitter.com/wnafee)
* [Ahmed Fouad](http://twitter.com/fo2ad)
* [Mahmoud Hossam](http://github.com/mahmoudhossam)

graphics by [Somaia Gabr](http://twitter.com/somaiagabr).

Arabic support for non-Arabic phones by [Rehab Mohamed](http://twitter.com/hams_rrr), based on Arabic Reshaper project by [Ahmed Essam](http://twitter.com/Neo_4583).

Code Style
------------------------
- tab size: 2
- indent : 2
- continuous indent: 4

you can set these under code style and code style java in android studio or 
intellij.


Setup
------------------------
1. get and install the [android sdk](http://developer.android.com/sdk/index.html)
2. make sure `$ANDROID_HOME` is set to the correct place.
3. build from the command line by running `./gradlew assembleDebug`
4. if you want an ide, download the latest version of [android studio](http://tools.android.com/download/studio/canary) (latest as of this writing is version 0.8.14)
5. run android studio, choose import project, then choose build.gradle from
the main quran source code directory.

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
you are free to use parts of the Quran Android code in your application
with some conditions:

* your app must be respectful of the book of Allah.  adding advertisements
above and below each page of Quran is unacceptable, for example (yes,
someone actually did that).

* your app must provide some significant value over our app - otherwise,
why not just contribute a patch instead?

* your app cannot make money from ads or from sales.  there are many reasons
for this:
    - we made this app for the benefit of people and not to make a profit
    - the app costs us money (for serving the pages, translations, etc), not to
mention the time to develop it and to support it.
    - not all of the data that we use is okay to be sold.  the images, along with
some of the translations are on a license that allows their use and distribution
for free for non-profit uses, but does not allow users to make money from them.

* if you write an application using any of the Quran data (the images, the
translations, etc), you must provide a link to the respective data source
page ([tanzil.net](http://tanzil.net) for the translations and the
[quran images project](http://github.com/quran/quran.com-images) for the images)
both within your application (in an about page) and in your application
description in the market or app store.

* if you use part of (or all of) the quran android code or graphics, you
must provide a link back to the [quran android
project](http://github.com/ahmedre/quran_android) in your application
description and your application itself in an about section.

Open Source Projects Used
-------------------------
* android-support library (support-v4 and appcompat-v7)
* [AndroidSlidingUpPanel](https://github.com/umano/AndroidSlidingUpPanel)
* [okhttp](https://github.com/square/okhttp)

Changelog
---------
**development**
- compile targeting sdk 21
- replace ActionBarSherlock with support-appcompat-v7
- improve share and copy ayah texts 
- toggle sura prefix using a boolean flag
- fix gapless repeat releated bugs #426
- fix translation highlighting color in night mode #423
- translation updates for Uzbek

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
