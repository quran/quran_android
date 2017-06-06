Quran for Android does not share any user information with third party entities.

Quran for Android used to request [READ_PHONE_STATE](https://developer.android.com/reference/android/Manifest.permission.html#READ_PHONE_STATE)
permission to stop recitation if a phone call comes in during an audio playback.
That was removed on September 27, 2015 [here](https://github.com/quran/quran_android/commit/f516199f5c1560beb2881f5d7f8d84f4ad325f23) (users running versions released prior to that day may still have it).
