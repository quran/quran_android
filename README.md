quran for android
==================================

this is a simple (madani based) quran app for android.
- images from [quran images project](http://github.com/quran/quran.com-images) on github.
- translation, tafsir and Arabic data come from [tanzil](http://tanzil.net) and [King Saud University](http://quran.ksu.edu.sa/).
- audio from [Every Ayah](http://everyayah.com/).

patches, comments, etc are welcome.

code by [Ahmed El-Helw](http://twitter.com/ahmedre),
[Ahmed Fouad](http://twitter.com/fo2ad),
[Hussein Maher](http://twitter.com/husseinmaher),
and [Wael Nafee](http://twitter.com/wnafee).

graphics by [Somaia Gabr](http://twitter.com/somaiagabr).

contributors include [Ahmed Farra](http://github.com/afarra) and [Mahmoud Hossam](http://github.com/mahmoudhossam).

Arabic support for non-Arabic phones by [Rehab Mohamed](http://twitter.com/hams_rrr), based on Arabic Reshaper project by [Ahmed Essam](http://twitter.com/Neo_4583).

Application localization
------------------------
Farsi translation by [@khajavi](http://github.com/khajavi).
Turkish translation by Mehmed Mahmudoglu.


Terms of use
------------
you are free to use parts of (or all of) the Quran Android code in your
application with some conditions:

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

Project dependecies
-------------------
*Action bar for Android:* https://github.com/johannilsson/android-actionbar

*Google's Gson library:* http://code.google.com/p/google-gson/


Changelog
---------
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
