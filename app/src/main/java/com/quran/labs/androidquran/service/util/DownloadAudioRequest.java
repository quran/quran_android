package com.quran.labs.androidquran.service.util;

import com.quran.labs.androidquran.common.QuranAyah;
import com.quran.labs.androidquran.util.AudioUtils;

public class DownloadAudioRequest extends AudioRequest {
   private static final long serialVersionUID = 1L;
   
   private int mQariId = -1;
   private String mLocalDirectoryPath = null;

   public DownloadAudioRequest(String baseUrl, QuranAyah verse,
                               int qariId, String localPath){
      super(baseUrl, verse);
      mQariId = qariId;
      mLocalDirectoryPath = localPath;
   }

   public int getQariId(){ return mQariId; }
   public String getLocalPath(){ return mLocalDirectoryPath; }

   @Override
   public boolean haveSuraAyah(int sura, int ayah){
      return AudioUtils.haveSuraAyahForQari(mLocalDirectoryPath, sura, ayah);
   }
}
