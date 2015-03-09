/* 
 * This code is based on the RandomMusicPlayer example from
 * the Android Open Source Project samples.  It has been modified
 * for use in Quran Android.
 *   
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.service.AudioService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/**
 * Receives broadcasted intents. In particular, we are interested in the
 * android.media.AUDIO_BECOMING_NOISY and android.intent.action.MEDIA_BUTTON
 * intents, which is broadcast, for example, when the user disconnects the
 * headphones. This class works because we are declaring it in a
 * &lt;receiver&gt; tag in AndroidManifest.xml.
 */
public class AudioIntentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(
                android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            //Toast.makeText(context, "Headphones disconnected.",
            //        Toast.LENGTH_SHORT).show();

            // send an intent to MusicService to tell it to pause the audio
            startAudioService(context, AudioService.ACTION_PAUSE);
        } else if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
            KeyEvent keyEvent = (KeyEvent) intent.getExtras().get(
                    Intent.EXTRA_KEY_EVENT);
            if (keyEvent == null ||
                keyEvent.getAction() != KeyEvent.ACTION_DOWN){
                return;
            }

            switch (keyEvent.getKeyCode()) {
                case KeyEvent.KEYCODE_HEADSETHOOK:
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    startAudioService(context, AudioService.ACTION_PLAYBACK);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                    startAudioService(context, AudioService.ACTION_PLAY);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    startAudioService(context, AudioService.ACTION_PAUSE);
                    break;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    startAudioService(context, AudioService.ACTION_STOP);
                    break;
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    startAudioService(context, AudioService.ACTION_SKIP);
                    break;
                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                    startAudioService(context, AudioService.ACTION_REWIND);
                    break;
            }
        }
    }

    private void startAudioService(Context context, String action) {
        final Intent intent = AudioService.getAudioIntent(context, action);
        context.startService(intent);
    }
}
