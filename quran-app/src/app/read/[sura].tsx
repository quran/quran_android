import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  FlatList,
  StyleSheet,
  Text,
  View,
  type ListViewToken,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { DisplaySheet } from '../../components/display';
import { TagDot, TagPickerSheet } from '../../components/tags';
import { Button, Icon, IconButton, Tap, tick } from '../../components/ui';
import { ARABIC_FONT, VerseActionsSheet } from '../../components/verse';
import {
  BISMILLAH,
  arabic,
  arabicNumber,
  juzOf,
  pageOf,
  refKey,
  sura as getSura,
  translation,
  transliteration,
  type Ref,
} from '../../data/quran';
import { actions, useStore, type Settings } from '../../state/store';
import { radius, usePalette } from '../../theme';

export default function Reader() {
  const params = useLocalSearchParams<{ sura: string; ayah?: string }>();
  const suraNo = Math.min(114, Math.max(1, Number(params.sura) || 1));
  const s = getSura(suraNo);
  const targetAyah = Math.min(s.count, Math.max(1, Number(params.ayah) || 1));

  const p = usePalette();
  const insets = useSafeAreaInsets();
  const settings = useStore((st) => st.settings);
  const lastRead = useStore((st) => st.lastRead);
  const saved = useStore((st) => st.saved);
  const tags = useStore((st) => st.tags);

  const listRef = useRef<FlatList<number>>(null);
  const [topAyah, setTopAyah] = useState(targetAyah);
  const [flash, setFlash] = useState<number | null>(targetAyah > 1 ? targetAyah : null);
  const [actionVerse, setActionVerse] = useState<Ref | null>(null);
  const [tagVerse, setTagVerse] = useState<Ref | null>(null);
  const [displayOpen, setDisplayOpen] = useState(false);

  const ayahs = useMemo(() => Array.from({ length: s.count }, (_, i) => i + 1), [s.count]);
  const tagColor = useMemo(() => new Map(tags.map((t) => [t.id, t.color])), [tags]);

  // jump to the requested ayah once the list has laid out
  useEffect(() => {
    setTopAyah(targetAyah);
    if (targetAyah > 1) {
      const t = setTimeout(() => {
        listRef.current?.scrollToIndex({ index: targetAyah - 1, animated: false, viewOffset: 8 });
      }, 60);
      return () => clearTimeout(t);
    }
    listRef.current?.scrollToOffset({ offset: 0, animated: false });
  }, [suraNo, targetAyah]);

  useEffect(() => {
    if (flash == null) return;
    const t = setTimeout(() => setFlash(null), 2200);
    return () => clearTimeout(t);
  }, [flash]);

  const onViewable = useRef(({ viewableItems }: { viewableItems: ListViewToken[] }) => {
    const first = viewableItems.find((v) => v.isViewable && typeof v.item === 'number');
    if (first) setTopAyah(first.item as number);
  }).current;

  // remember where you are automatically (separate from your manual "last read")
  useEffect(() => {
    const t = setTimeout(() => actions.setRecent({ sura: suraNo, ayah: topAyah }), 800);
    return () => clearTimeout(t);
  }, [suraNo, topAyah]);

  const goToSura = (n: number) => {
    router.replace({ pathname: '/read/[sura]', params: { sura: String(n) } });
  };

  const topRef = { sura: suraNo, ayah: topAyah };
  const lastReadHere = lastRead?.sura === suraNo ? lastRead.ayah : null;
  const markedOnScreen = lastReadHere != null && Math.abs(lastReadHere - topAyah) <= 1;

  const renderItem = useCallback(
    ({ item: ayah }: { item: number }) => {
      const key = refKey({ sura: suraNo, ayah });
      const entry = saved[key];
      const newPage = ayah > 1 && pageOf({ sura: suraNo, ayah }) !== pageOf({ sura: suraNo, ayah: ayah - 1 });
      return (
        <AyahView
          sura={suraNo}
          ayah={ayah}
          settings={settings}
          isLastRead={lastReadHere === ayah}
          flash={flash === ayah}
          tagColors={entry ? entry.tagIds.map((id) => tagColor.get(id) ?? p.accent) : []}
          pageBreak={newPage ? pageOf({ sura: suraNo, ayah }) : null}
          onPress={() => setActionVerse({ sura: suraNo, ayah })}
        />
      );
    },
    [suraNo, saved, settings, lastReadHere, flash, tagColor, p.accent]
  );

  return (
    <View style={{ flex: 1, backgroundColor: p.bg }}>
      <View
        style={[
          styles.header,
          { paddingTop: insets.top + 6, backgroundColor: p.bg, borderBottomColor: p.border },
        ]}
      >
        <IconButton
          name="chevron-back"
          label="Back"
          onPress={() => (router.canGoBack() ? router.back() : router.replace('/'))}
        />
        <View style={{ flex: 1, alignItems: 'center' }}>
          <Text style={[styles.headerTitle, { color: p.text }]} numberOfLines={1}>
            {s.tr}
          </Text>
          <Text style={[styles.headerSub, { color: p.textMuted }]}>
            Ayah {topAyah} · Page {pageOf(topRef)} · Juz {juzOf(topRef)}
          </Text>
        </View>
        <IconButton name="text" label="Display settings" onPress={() => setDisplayOpen(true)} />
        <IconButton
          name={markedOnScreen ? 'bookmark' : 'bookmark-outline'}
          color={p.gold}
          label="Mark this spot as last read"
          onPress={() => {
            tick();
            actions.setLastRead(topRef);
          }}
        />
      </View>

      <FlatList
        ref={listRef}
        data={ayahs}
        keyExtractor={(a) => String(a)}
        renderItem={renderItem}
        extraData={renderItem}
        onViewableItemsChanged={onViewable}
        viewabilityConfig={{ itemVisiblePercentThreshold: 40 }}
        initialNumToRender={Math.min(s.count, Math.max(12, targetAyah + 4))}
        windowSize={11}
        onScrollToIndexFailed={(info) => {
          listRef.current?.scrollToOffset({
            offset: info.averageItemLength * info.index,
            animated: false,
          });
          setTimeout(
            () => listRef.current?.scrollToIndex({ index: info.index, animated: false, viewOffset: 8 }),
            120
          );
        }}
        ListHeaderComponent={<SuraHeader n={suraNo} />}
        ListFooterComponent={
          <View style={styles.footer}>
            {suraNo > 1 && (
              <Button
                title={`${getSura(suraNo - 1).tr}`}
                icon="arrow-back"
                kind="soft"
                style={{ flex: 1 }}
                onPress={() => goToSura(suraNo - 1)}
              />
            )}
            {suraNo < 114 && (
              <Button
                title={`Next: ${getSura(suraNo + 1).tr}`}
                kind="primary"
                style={{ flex: 1 }}
                onPress={() => goToSura(suraNo + 1)}
              />
            )}
          </View>
        }
        contentContainerStyle={[styles.list, { paddingBottom: insets.bottom + 100 }]}
      />

      {!markedOnScreen && (
        <Tap
          onPress={() => {
            tick();
            actions.setLastRead(topRef);
          }}
          style={[styles.fab, { backgroundColor: p.gold, bottom: insets.bottom + 20 }]}
          accessibilityLabel="Mark this spot as last read"
        >
          <Icon name="bookmark" size={18} color="#fff" />
          <Text style={styles.fabText}>Mark ayah {topAyah} as last read</Text>
        </Tap>
      )}

      <VerseActionsSheet
        verse={actionVerse}
        onClose={() => setActionVerse(null)}
        onSaveToTag={setTagVerse}
      />
      <TagPickerSheet verse={tagVerse} onClose={() => setTagVerse(null)} />
      <DisplaySheet visible={displayOpen} onClose={() => setDisplayOpen(false)} />
    </View>
  );
}

function SuraHeader({ n }: { n: number }) {
  const p = usePalette();
  const s = getSura(n);
  return (
    <View>
      <View style={[styles.suraCard, { backgroundColor: p.surface, borderColor: p.border }]}>
        <Text style={[styles.suraArabic, { color: p.arabic, fontFamily: ARABIC_FONT }]}>
          سُورَةُ {s.ar}
        </Text>
        <Text style={[styles.suraMeaning, { color: p.text }]}>
          {s.tr} — “{s.en}”
        </Text>
        <Text style={[styles.suraMeta, { color: p.textMuted }]}>
          Surah {n} · {s.count} ayahs · {s.type === 'meccan' ? 'Makki' : 'Madani'} · Page{' '}
          {pageOf({ sura: n, ayah: 1 })}
        </Text>
      </View>
      {n !== 1 && n !== 9 && (
        <Text style={[styles.bismillah, { color: p.arabic, fontFamily: ARABIC_FONT }]}>{BISMILLAH}</Text>
      )}
    </View>
  );
}

function AyahView({
  sura,
  ayah,
  settings,
  isLastRead,
  flash,
  tagColors,
  pageBreak,
  onPress,
}: {
  sura: number;
  ayah: number;
  settings: Settings;
  isLastRead: boolean;
  flash: boolean;
  tagColors: string[];
  pageBreak: number | null;
  onPress: () => void;
}) {
  const p = usePalette();
  const t = translation(settings.translation, sura, ayah);
  const isUrdu = settings.translation === 'ur';
  return (
    <View>
      {pageBreak != null && (
        <View style={styles.pageBreak}>
          <View style={[styles.pageLine, { backgroundColor: p.border }]} />
          <Text style={[styles.pageText, { color: p.textFaint }]}>Page {pageBreak}</Text>
          <View style={[styles.pageLine, { backgroundColor: p.border }]} />
        </View>
      )}
      <Tap
        onPress={onPress}
        onLongPress={onPress}
        pressedOpacity={0.75}
        style={[
          styles.ayah,
          { borderColor: 'transparent' },
          isLastRead && { backgroundColor: p.goldSoft, borderColor: p.gold },
          flash && !isLastRead && { backgroundColor: p.accentSoft },
        ]}
        accessibilityHint="Opens actions for this verse"
      >
        <View style={styles.ayahTop}>
          <View style={[styles.ayahNo, { backgroundColor: p.surfaceAlt }]}>
            <Text style={[styles.ayahNoText, { color: p.textMuted }]}>
              {sura}:{ayah}
            </Text>
          </View>
          {isLastRead && (
            <View style={[styles.lastReadPill, { backgroundColor: p.gold }]}>
              <Icon name="bookmark" size={12} color="#fff" />
              <Text style={styles.lastReadText}>Last read</Text>
            </View>
          )}
          <View style={{ flex: 1 }} />
          {tagColors.slice(0, 4).map((c, i) => (
            <TagDot key={i} color={c} size={10} />
          ))}
          <Icon name="ellipsis-horizontal" size={20} color={p.textFaint} />
        </View>
        <Text
          style={[
            styles.arabic,
            {
              color: p.arabic,
              fontFamily: ARABIC_FONT,
              fontSize: settings.arabicSize,
              lineHeight: settings.arabicSize * 2.1,
            },
          ]}
        >
          {arabic(sura, ayah)}
          <Text style={{ color: p.accent }}> ﴿{arabicNumber(ayah)}﴾</Text>
        </Text>
        {settings.showTransliteration && (
          <Text
            style={[
              styles.translit,
              { color: p.textMuted, fontSize: settings.translationSize - 1, lineHeight: (settings.translationSize - 1) * 1.5 },
            ]}
          >
            {transliteration(sura, ayah)}
          </Text>
        )}
        {t ? (
          <Text
            style={[
              {
                color: p.text,
                fontSize: isUrdu ? settings.translationSize + 2 : settings.translationSize,
                lineHeight: (isUrdu ? settings.translationSize + 2 : settings.translationSize) * (isUrdu ? 1.9 : 1.55),
                marginTop: 6,
              },
              isUrdu && { writingDirection: 'rtl', textAlign: 'right' },
            ]}
          >
            {t}
          </Text>
        ) : null}
      </Tap>
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingBottom: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
    gap: 2,
  },
  headerTitle: { fontSize: 18, fontWeight: '800' },
  headerSub: { fontSize: 12.5, marginTop: 1 },
  list: { paddingHorizontal: 12, paddingTop: 12, width: '100%', maxWidth: 760, alignSelf: 'center' },
  suraCard: {
    alignItems: 'center',
    paddingVertical: 18,
    paddingHorizontal: 16,
    borderRadius: radius.xl,
    borderWidth: 1,
  },
  suraArabic: { fontSize: 34, lineHeight: 70 },
  suraMeaning: { fontSize: 17, fontWeight: '700' },
  suraMeta: { fontSize: 13.5, marginTop: 4, textAlign: 'center' },
  bismillah: { fontSize: 28, lineHeight: 64, textAlign: 'center', marginTop: 14, marginBottom: 4 },
  pageBreak: { flexDirection: 'row', alignItems: 'center', gap: 10, marginVertical: 6, paddingHorizontal: 8 },
  pageLine: { flex: 1, height: 1 },
  pageText: { fontSize: 12, fontWeight: '700', letterSpacing: 1 },
  ayah: {
    paddingHorizontal: 12,
    paddingVertical: 14,
    borderRadius: radius.lg,
    borderWidth: 1.5,
    marginVertical: 2,
  },
  ayahTop: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 4 },
  ayahNo: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  ayahNoText: { fontSize: 12.5, fontWeight: '700' },
  lastReadPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 999,
  },
  lastReadText: { color: '#fff', fontSize: 12, fontWeight: '700' },
  arabic: { writingDirection: 'rtl', textAlign: 'right' },
  translit: { fontStyle: 'italic', marginTop: 4 },
  footer: { flexDirection: 'row', gap: 10, marginTop: 24 },
  fab: {
    position: 'absolute',
    alignSelf: 'center',
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 18,
    paddingVertical: 12,
    borderRadius: 999,
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 6,
  },
  fabText: { color: '#fff', fontSize: 15, fontWeight: '700' },
});
