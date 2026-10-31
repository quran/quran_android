import { router } from 'expo-router';
import { useDeferredValue, useMemo, useState } from 'react';
import { FlatList, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Icon, IconButton, SectionLabel, Tap } from '../components/ui';
import { ARABIC_FONT, VerseCard, openVerse } from '../components/verse';
import {
  TOTAL_PAGES,
  findSuras,
  pageStart,
  parseRef,
  searchVerses,
  sura,
  type Ref,
  type Sura,
  type VerseHit,
} from '../data/quran';
import { useStore } from '../state/store';
import { radius, usePalette } from '../theme';

type Item =
  | { kind: 'goto'; ref: Ref; label: string }
  | { kind: 'sura'; s: Sura }
  | { kind: 'label'; text: string }
  | { kind: 'verse'; hit: VerseHit };

export default function Search() {
  const p = usePalette();
  const insets = useSafeAreaInsets();
  const translationId = useStore((s) => s.settings.translation);
  const [q, setQ] = useState('');
  const query = useDeferredValue(q);

  const items: Item[] = useMemo(() => {
    const t = query.trim();
    if (!t) return [];
    const out: Item[] = [];
    const ref = parseRef(t);
    if (ref) out.push({ kind: 'goto', ref, label: `Go to ${sura(ref.sura).tr} ${ref.sura}:${ref.ayah}` });
    const pageMatch = t.match(/^p(?:age)?\s*(\d{1,3})$/i);
    if (pageMatch) {
      const n = Number(pageMatch[1]);
      if (n >= 1 && n <= TOTAL_PAGES) out.push({ kind: 'goto', ref: pageStart(n), label: `Go to page ${n}` });
    }
    if (!/^\d/.test(t)) {
      const suras = findSuras(t).slice(0, 6);
      if (suras.length) {
        out.push({ kind: 'label', text: 'Surahs' });
        suras.forEach((s) => out.push({ kind: 'sura', s }));
      }
    }
    const hits = searchVerses(t, translationId);
    if (hits.length) {
      out.push({ kind: 'label', text: hits.length >= 60 ? 'Verses (first 60)' : `Verses (${hits.length})` });
      hits.forEach((hit) => out.push({ kind: 'verse', hit }));
    }
    return out;
  }, [query, translationId]);

  return (
    <View style={{ flex: 1, backgroundColor: p.bg, paddingTop: insets.top + 6 }}>
      <View style={styles.bar}>
        <IconButton
          name="chevron-back"
          label="Back"
          onPress={() => (router.canGoBack() ? router.back() : router.replace('/'))}
        />
        <View style={[styles.field, { backgroundColor: p.surfaceAlt }]}>
          <Icon name="search" size={18} color={p.textFaint} />
          <TextInput
            value={q}
            onChangeText={setQ}
            autoFocus
            placeholder="Surah, 2:255, page 50, or any word"
            placeholderTextColor={p.textFaint}
            returnKeyType="search"
            style={[styles.input, { color: p.text }]}
          />
          {q ? <IconButton name="close-circle" size={18} label="Clear" onPress={() => setQ('')} /> : null}
        </View>
      </View>
      <FlatList
        data={items}
        keyboardShouldPersistTaps="handled"
        keyExtractor={(it, i) => `${it.kind}${i}`}
        contentContainerStyle={[styles.content, { paddingBottom: insets.bottom + 32 }]}
        ListEmptyComponent={
          <View style={{ paddingTop: 24, gap: 14 }}>
            {q.trim().length > 0 && query.trim().length >= 3 ? (
              <Text style={{ color: p.textMuted, textAlign: 'center' }}>Nothing found for “{query}”.</Text>
            ) : (
              <>
                <SectionLabel>Try</SectionLabel>
                {['Al-Kahf', '2:255', 'page 1', 'mercy', 'patience'].map((ex) => (
                  <Tap key={ex} onPress={() => setQ(ex)} style={[styles.hint, { borderColor: p.border }]}>
                    <Icon name="sparkles-outline" size={16} color={p.accent} />
                    <Text style={{ color: p.text, fontSize: 15 }}>{ex}</Text>
                  </Tap>
                ))}
              </>
            )}
          </View>
        }
        renderItem={({ item }) => {
          switch (item.kind) {
            case 'goto':
              return (
                <Tap
                  onPress={() => openVerse(item.ref)}
                  style={[styles.goto, { backgroundColor: p.accent }]}
                >
                  <Icon name="navigate" size={18} color={p.onAccent} />
                  <Text style={{ color: p.onAccent, fontSize: 16, fontWeight: '700', flex: 1 }}>{item.label}</Text>
                  <Icon name="arrow-forward" size={18} color={p.onAccent} />
                </Tap>
              );
            case 'label':
              return <SectionLabel style={{ marginTop: 18 }}>{item.text}</SectionLabel>;
            case 'sura':
              return (
                <Tap
                  onPress={() => openVerse({ sura: item.s.n, ayah: 1 })}
                  style={[styles.sura, { borderBottomColor: p.border }]}
                >
                  <Text style={[styles.suraNo, { color: p.accent }]}>{item.s.n}</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={{ color: p.text, fontSize: 16, fontWeight: '700' }}>{item.s.tr}</Text>
                    <Text style={{ color: p.textMuted, fontSize: 13 }}>
                      {item.s.en} · {item.s.count} ayahs
                    </Text>
                  </View>
                  <Text style={{ color: p.arabic, fontFamily: ARABIC_FONT, fontSize: 22, lineHeight: 42 }}>
                    {item.s.ar}
                  </Text>
                </Tap>
              );
            case 'verse':
              return (
                <View style={{ marginBottom: 10 }}>
                  <VerseCard verse={item.hit} highlight={query} />
                </View>
              );
          }
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 8, paddingBottom: 8 },
  field: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingLeft: 14,
    borderRadius: radius.md,
    minHeight: 46,
  },
  input: { flex: 1, fontSize: 16, paddingVertical: 10 },
  content: { paddingHorizontal: 16, width: '100%', maxWidth: 720, alignSelf: 'center' },
  hint: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderWidth: 1,
    borderRadius: radius.md,
  },
  goto: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 16,
    borderRadius: radius.lg,
    marginTop: 8,
  },
  sura: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 10,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  suraNo: { width: 30, fontSize: 15, fontWeight: '800', textAlign: 'center' },
});
