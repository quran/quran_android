quran for android
==================================

this is a simple (madani based) quran app for android.

* images from [quran images project](http://github.com/quran/quran.com-images) on github.
* translation, tafsir and Arabic data come from [tanzil](http://tanzil.net) and [King Saud University](http://quran.ksu.edu.sa/).
* audio from [Every Ayah](http://everyayah.com/).

patches, comments, etc are welcome.

contributors:

* [Hussein Maher](http://twitter.com/husseinmaher),
* [Ahmed Farra](http://github.com/afarra),
* [Wael Nafee](http://twitter.com/wnafee),
* [Ahmed Fouad](http://twitter.com/fo2ad),
* [Mahmoud Hossam](http://github.com/mahmoudhossam).

graphics by [Somaia Gabr](http://twitter.com/somaiagabr).

Arabic support for non-Arabic phones by [Rehab Mohamed](http://twitter.com/hams_rrr), based on Arabic Reshaper project by [Ahmed Essam](http://twitter.com/Neo_4583).

App localization
------------------------

* Farsi for version 2.0 by M. Jafar Nakar
* Farsi for version 1.6 by [@khajavi](http://github.com/khajavi).
* Turkish by Mehmed Mahmudoglu.
* Russian by Rinat (Ринат Валеев).
* Kurdish by Goran Gharib Karim.
* French by Yasser [@yasserkad](http://github.com/yasserkad).
* German by Armin Supuk
* Chinese by Bo Li


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
* [maven-android-plugin](http://code.google.com/p/maven-android-plugin/)

Changelog
---------
**development**

- update to german translation (thanks br Armin Supuk)
- kurdish translation (thanks br Gharib Karim)
- improvements to search
- multiple sdcard support
- tablet support (experimental)
- change color of text in translation view and night mode

**version 2.3.1** (released 4/2/2013)

- re-add long-press support on translation text
- fix several crashes based on google play console
- remove sura name translations in various languages

**version 2.3.0** (released 3/31/2013)

- sheikh Sudais gapless (thanks br Redouane Chaar)
- tag improvements (batch tag operations, etc) [\#235](https://github.com/ahmedre/quran_android/issues/235)
- anti-alias overlay text (thanks @boussouira)
- bookmark and highlighting code improvements and bugfixes
- french translation (thanks @yasserkad)
- chinese translation (thanks Bo Li)
- german translation (thanks @ArminSupuk)
- numbers are now localized properly
- [quranapp.com](http://quranapp.com) sharing support
- translation text highlights when audio is playing [\#254](https://github.com/ahmedre/quran_android/issues/254)
- assorted bugfixes

**version 2.2.1** (released 12/31/2012)

- sort bookmarks by date added or by location in the Quran
- bugfixes - [\#234](https://github.com/ahmedre/quran_android/issues/234)

**version 2.2.0**

- fast switching between translations [\#218](https://github.com/ahmedre/quran_android/issues/218)
- upgrade process for translations
- move to maven
- migrate audio files to `/sdcard/quran_android/audio` instead of temp app directory
- bookmarks changes
- exposed an intent to allow launching Quran directly to a
  page/verse - [\#183](https://github.com/ahmedre/quran_android/issues/183)
- navigation using volume keys - [\#172](https://github.com/ahmedre/quran_android/issues/172)
- fix navigation bar jumping on jellybean
- option to overlay page number, sura name, and juz' on the page - [\#159](https://github.com/ahmedre/quran_android/issues/159)
- fix actionbar not toggling on translation view on honeycomb+ - [\#158](https://github.com/ahmedre/quran_android/issues/158)
- fix ldpi devices not being able to download over 3g in certain cases - [\#167](https://github.com/ahmedre/quran_android/issues/167)
- relax constraint on deciding whether or not data is downloaded - [\#196](https://github.com/ahmedre/quran_android/issues/196)

**version 2.1.0**

- setting to bring back the old background color
- the "jump to" dialog is back!
- support for deleting bookmarks from the bookmarks screen
- setting for those who have their arabic render backwards
- app is now localized in Russian and Farsi

**version 2.0.2**

- exactly like 2.0.1, just compiled with utf8 files to fix arabic

**version 2.0.1**

- fix market crashes

**version 2.0.0**

- new, improved ui and code rewrite
- gapless audio support
- ayah actions (bookmark, share, copy, tafseer)
- only supports sdks 2.1+

**version 1.6.1**

- download issue fixed
- restore locale issue fixed

**version 1.6.0**

- search!  searches arabic text and translations!
- beta: audio repeat feature!
- beta: night mode!
- farsi translation now available, and app now localized in farsi (thanks khajavi).
- full arabic ui
- better images for ldpi devices.
- looks nicer now on tablet and large devices.
- bugfixes for ICS and 1.5 devices.
- many, many bugfixes and minor improvements.

**version 1.5.2**

- fix crash on android 1.5.
- rub3/7izb/juz2 notifications while reading
- autoscroll to ayah if it is not visible on the screen
- audio options to resume from last played ayah, start of the page, or start
  of any of the suras on that page.
- apps2sd support.
- plethora of bugfixes (arabic fixes, seekbar not refreshing after jump, page
  resets when orientation changes in translation view, page navigated to in
  translation not retained when returning to quran view, and warning if the
  sd card is filled).

**version 1.5**

- audio support
- highlight ayah

**version 1.4**

- smooth transition between pages
- resume download support

**version 1.3**

- improved interface
- support for 1024x768 images
- translation download support
- arabic support for non-arabic supporting devices
- initial search support via search button
- more translations
- bugfixes and more

**version 1.2**

- Sahih Internation Translation introduced
- Fix orientation in either landscape or portrait modes
- Adjust translation text size
- Centralized menu for app
- Bookmarks are added via menu
- Fixed bookmarks bug

**version 1.1**

- added bookmarks
- updated browse to allow browsing by juz'
- remember the last place you left off
- added help dialog
- made the screen lock an option
- fixed a bug where the screen lock wasn't released

**version 1.0**

- initial release
