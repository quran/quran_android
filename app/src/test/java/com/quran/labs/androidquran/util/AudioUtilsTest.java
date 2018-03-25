package com.quran.labs.androidquran.util;

import com.quran.data.pageinfo.common.MadaniDataSource;
import com.quran.data.source.PageProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class AudioUtilsTest {

  @Test
  public void testGetLastAyahWithNewSurahOnNextPageForMadani() throws Exception {
    PageProvider pageProviderMock = Mockito.mock(PageProvider.class);
    when(pageProviderMock.getDataSource())
        .thenReturn(new MadaniDataSource());

    QuranInfo quranInfoMock = new QuranInfo(pageProviderMock);

    AudioUtils audioUtils = new AudioUtils(quranInfoMock, null);
    SuraAyah lastAyah = audioUtils.getLastAyahToPlay(new SuraAyah(109, 1), 603, 1, false);
    assertTrue(lastAyah.ayah == 5);
    assertTrue(lastAyah.sura == 111);
  }
}
