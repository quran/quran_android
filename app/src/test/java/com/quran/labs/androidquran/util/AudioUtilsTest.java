package com.quran.labs.androidquran.util;

import com.quran.data.page.provider.madani.MadaniDataSource;
import com.quran.data.source.PageProvider;
import com.quran.labs.androidquran.data.QuranInfo;
import com.quran.labs.androidquran.data.SuraAyah;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * Created by akamal on 3/25/18.
 */
public class AudioUtilsTest {

  @InjectMocks
  QuranInfo quranInfoMock;
  @Test
  public void testGetLastAyahWithNewSurahOnNextPageForMadani() throws Exception {
    PageProvider pageProviderMock = Mockito.mock(PageProvider.class);
    when(pageProviderMock.getDataSource())
        .thenReturn(new MadaniDataSource());

    quranInfoMock = new QuranInfo(pageProviderMock);

    AudioUtils audioUtils = new AudioUtils(quranInfoMock, null);
    SuraAyah lastAyah = audioUtils.getLastAyahToPlay(new SuraAyah(109, 1), 603, 1, false);
    assertTrue(lastAyah.ayah == 5);
    assertTrue(lastAyah.sura == 111);
  }
}
