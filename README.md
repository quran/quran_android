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
1. download the latest canary version of [android studio](http://tools.android.com/download/studio/canary) (latest as of this writing is version 0.5.1)
2. make sure you have the android sdk installed, along with the sdk tools,
platform-tools, android 4.2.2 sdk platform, android support repository, android support library, and google repository.
3. make a file called `local.properties` with one line in it:
`sdk.dir=/path/to/your/android/sdk` 
(make sure to replace `/path/to/your/android/sdk` with the directory to which you downloaded the android sdk).
4. run android studio, choose import project, then choose build.gradle from
the main quran source code directory.
5. if you want to build from the command line instead, you can build by running `./gradlew assembleDebug` (after completing steps 1-3 above).

and that's it!

App localization
------------------------

* Farsi for version 2.0 by M. Jafar Nakar
* Farsi for version 1.6 by [khajavi](http://github.com/khajavi).
* Turkish by Mehmed Mahmudoglu.
* Russian by Rinat (Ринат Валеев).
* Kurdish by Goran Gharib Karim.
* French by Yasser [yasserkad](http://github.com/yasserkad).
* German by [Armin Supuk](http://github.com/ArminSupuk).
* Chinese by Bo Li
* Uyghur by Abduqadir Abliz [Sahran](http://github.com/Sahran)
* Indonesian by [Saiful Khaliq](http://twitter.com/saifious)
* Malaysian by [Ahmad Syazwan](https://github.com/asyazwan)


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
* [ActionBarSherlock](http://abs.io)
* [AndroidSlidingUpPanel](https://github.com/umano/AndroidSlidingUpPanel)
* [NineOldAndroids](https://github.com/JakeWharton/NineOldAndroids)

Changelog
---------
**current**
- major ui improvements for long press actions
- option to highlight ayah bookmarks by default
- malaysian translation (thanks @asyazwan)
- uyghur translation (thanks @Sahran)
- right to left layout for arabic (thanks @aessam)
- bugfixes

You can see our complete set of releases [here] (https://github.com/ahmedre/quran_android/releases)
