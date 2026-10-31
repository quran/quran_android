import { router } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { TagDot, TagEditorSheet } from '../components/tags';
import { Button, EmptyState, Icon, IconButton, Segmented, Tap } from '../components/ui';
import { ARABIC_FONT, openVerse } from '../components/verse';
import {
  JUZ,
  arabic,
  findSuras,
  juzOf,
  pageOf,
  progressOf,
  sura,
  type Juz,
  type Sura,
} from '../data/quran';
import { useStore, type Place, type Tag } from '../state/store';
import { radius, usePalette } from '../theme';

type TabId = 'suras' | 'juz' | 'saved';
type Row =
  | { kind: 'sura'; sura: Sura }
  | { kind: 'juz'; juz: Juz }
  | { kind: 'all'; count: number }
  | { kind: 'tag'; tag: Tag; count: number }
  | { kind: 'newTag' }
  | { kind: 'empty' };

function timeAgo(at: number) {
  const m = Math.round((Date.now() - at) / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.round(h / 24);
  return d === 1 ? 'yesterday' : `${d} days ago`;
}

export default function Home() {
  const p = usePalette();
  const insets = useSafeAreaInsets();
  const [tab, setTab] = useState<TabId>('suras');
  const [filter, setFilter] = useState('');
  const [editing, setEditing] = useState<Tag | null | undefined>(undefined);
  const tags = useStore((s) => s.tags);
  const saved = useStore((s) => s.saved);
  const lastRead = useStore((s) => s.lastRead);

  const rows: Row[] = useMemo(() => {
    if (tab === 'suras') {
      return findSuras(filter).map((s) => ({ kind: 'sura', sura: s }) as Row);
    }
    if (tab === 'juz') return JUZ.map((j) => ({ kind: 'juz', juz: j }) as Row);
    const all = Object.values(saved);
    if (tags.length === 0) return [{ kind: 'empty' }];
    const counts = new Map<string, number>();
    for (const v of all) for (const t of v.tagIds) counts.set(t, (counts.get(t) ?? 0) + 1);
    return [
      { kind: 'all', count: all.length },
      ...[...tags]
        .sort((a, b) => a.name.localeCompare(b.name))
        .map((t) => ({ kind: 'tag', tag: t, count: counts.get(t.id) ?? 0 }) as Row),
      { kind: 'newTag' },
    ];
  }, [tab, filter, saved, tags]);

  const header = (
    <View>
      <View style={styles.topBar}>
        <View style={{ flex: 1 }}>
          <Text style={[styles.greeting, { color: p.textMuted }]}>Assalamu alaikum</Text>
          <Text style={[styles.title, { color: p.text }]}>Al-Qur’an</Text>
        </View>
        <IconButton name="search" label="Search" filled onPress={() => router.push('/search')} />
        <IconButton
          name="settings-outline"
          label="Settings"
          filled
          onPress={() => router.push('/settings')}
        />
      </View>

      <ContinueCard />

      <View style={{ marginTop: 22, marginBottom: 12 }}>
        <Segmented<TabId>
          value={tab}
          onChange={setTab}
          options={[
            { value: 'suras', label: 'Surahs', icon: 'list' },
            { value: 'juz', label: 'Juz', icon: 'layers-outline' },
            { value: 'saved', label: 'Saved', icon: 'pricetags-outline' },
          ]}
        />
      </View>

      {tab === 'suras' && (
        <View style={[styles.filter, { backgroundColor: p.surfaceAlt }]}>
          <Icon name="funnel-outline" size={18} color={p.textFaint} />
          <TextInput
            value={filter}
            onChangeText={setFilter}
            placeholder="Filter by name or number"
            placeholderTextColor={p.textFaint}
            style={[styles.filterInput, { color: p.text }]}
          />
          {filter ? (
            <IconButton name="close-circle" size={18} label="Clear" onPress={() => setFilter('')} />
          ) : null}
        </View>
      )}
    </View>
  );

  const renderItem = ({ item }: { item: Row }) => {
    switch (item.kind) {
      case 'sura':
        return <SuraRow s={item.sura} lastRead={lastRead} />;
      case 'juz':
        return <JuzRow j={item.juz} lastRead={lastRead} />;
      case 'all':
        return (
          <TagRow
            title="All saved verses"
            count={item.count}
            icon
            onPress={() => router.push({ pathname: '/tag/[id]', params: { id: 'all' } })}
          />
        );
      case 'tag':
        return (
          <TagRow
            title={item.tag.name}
            color={item.tag.color}
            count={item.count}
            onPress={() => router.push({ pathname: '/tag/[id]', params: { id: item.tag.id } })}
            onLongPress={() => setEditing(item.tag)}
          />
        );
      case 'newTag':
        return (
          <Button
            title="New tag"
            icon="add"
            kind="soft"
            onPress={() => setEditing(null)}
            style={{ marginTop: 12 }}
          />
        );
      case 'empty':
        return (
          <EmptyState
            icon="pricetags-outline"
            title="Keep verses under your own tags"
            body="Make tags like “Patience”, “Duas” or “To memorise”. While reading, tap any verse and choose “Save to a tag”."
            action={<Button title="Create your first tag" icon="add" onPress={() => setEditing(null)} />}
          />
        );
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: p.bg }}>
      <FlatList
        data={rows}
        keyExtractor={(r, i) =>
          r.kind === 'sura' ? `s${r.sura.n}` : r.kind === 'juz' ? `j${r.juz.n}` : r.kind === 'tag' ? r.tag.id : `${r.kind}${i}`
        }
        renderItem={renderItem}
        ListHeaderComponent={header}
        ListEmptyComponent={
          <Text style={{ color: p.textMuted, textAlign: 'center', marginTop: 24 }}>
            No surah matches “{filter}”.
          </Text>
        }
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={[
          styles.content,
          { paddingTop: insets.top + 8, paddingBottom: insets.bottom + 32 },
        ]}
        initialNumToRender={20}
      />
      <TagEditorSheet
        visible={editing !== undefined}
        tag={editing}
        onClose={() => setEditing(undefined)}
      />
    </View>
  );
}

function ContinueCard() {
  const p = usePalette();
  const lastRead = useStore((s) => s.lastRead);
  const recent = useStore((s) => s.recent);

  // only worth showing when you've wandered somewhere else since setting your marker
  const showRecent =
    !!recent &&
    !!lastRead &&
    recent.at > lastRead.at &&
    (recent.sura !== lastRead.sura || Math.abs(recent.ayah - lastRead.ayah) > 3);

  if (!lastRead) {
    return (
      <View style={[styles.card, { backgroundColor: p.accent }]}>
        <View style={styles.cardRow}>
          <Icon name="bookmark-outline" size={20} color={p.onAccent} />
          <Text style={[styles.cardLabel, { color: p.onAccent }]}>LAST READ</Text>
        </View>
        <Text style={[styles.cardTitle, { color: p.onAccent }]}>Set your place</Text>
        <Text style={[styles.cardHint, { color: p.onAccent }]}>
          Open any surah, tap a verse and choose “Mark as last read”. It will show up here.
        </Text>
        <View style={{ flexDirection: 'row', gap: 10, marginTop: 14 }}>
          <Tap
            onPress={() => openVerse(recent ?? { sura: 1, ayah: 1 })}
            style={[styles.cardButton, { backgroundColor: p.onAccent }]}
          >
            <Text style={[styles.cardButtonText, { color: p.accent }]}>
              {recent ? `Resume ${sura(recent.sura).tr} ${recent.sura}:${recent.ayah}` : 'Start with Al-Fatihah'}
            </Text>
            <Icon name="arrow-forward" size={18} color={p.accent} />
          </Tap>
        </View>
      </View>
    );
  }

  const s = sura(lastRead.sura);
  const pct = Math.max(2, Math.round(progressOf(lastRead) * 100));
  return (
    <View>
      <Tap
        onPress={() => openVerse(lastRead)}
        pressedOpacity={0.85}
        style={[styles.card, { backgroundColor: p.accent }]}
        accessibilityLabel={`Continue reading ${s.tr} ayah ${lastRead.ayah}`}
      >
        <View style={styles.cardRow}>
          <Icon name="bookmark" size={18} color={p.onAccent} />
          <Text style={[styles.cardLabel, { color: p.onAccent }]}>LAST READ · {timeAgo(lastRead.at)}</Text>
        </View>
        <View style={[styles.cardRow, { alignItems: 'flex-end', marginTop: 6 }]}>
          <View style={{ flex: 1 }}>
            <Text style={[styles.cardTitle, { color: p.onAccent }]} numberOfLines={1}>
              {s.tr}
            </Text>
            <Text style={[styles.cardMeta, { color: p.onAccent }]}>
              Ayah {lastRead.ayah} of {s.count} · Page {pageOf(lastRead)} · Juz {juzOf(lastRead)}
            </Text>
          </View>
          <Text style={[styles.cardArabic, { color: p.onAccent, fontFamily: ARABIC_FONT }]}>{s.ar}</Text>
        </View>
        <Text
          numberOfLines={1}
          style={[styles.cardVerse, { color: p.onAccent, fontFamily: ARABIC_FONT }]}
        >
          {arabic(lastRead.sura, lastRead.ayah)}
        </Text>
        <View style={[styles.progressTrack, { backgroundColor: p.onAccent + '33' }]}>
          <View style={[styles.progressFill, { width: `${pct}%`, backgroundColor: p.onAccent }]} />
        </View>
        <View style={[styles.cardRow, { marginTop: 14, justifyContent: 'space-between' }]}>
          <Text style={[styles.cardMeta, { color: p.onAccent }]}>{pct}% through the Qur’an</Text>
          <View style={[styles.cardButton, { backgroundColor: p.onAccent }]}>
            <Text style={[styles.cardButtonText, { color: p.accent }]}>Continue</Text>
            <Icon name="arrow-forward" size={18} color={p.accent} />
          </View>
        </View>
      </Tap>
      {showRecent && recent && <RecentStrip recent={recent} />}
    </View>
  );
}

function RecentStrip({ recent }: { recent: Place }) {
  const p = usePalette();
  return (
    <Tap
      onPress={() => openVerse(recent)}
      style={[styles.recent, { backgroundColor: p.surface, borderColor: p.border }]}
    >
      <Icon name="time-outline" size={18} color={p.textMuted} />
      <Text style={{ flex: 1, color: p.textMuted, fontSize: 14 }} numberOfLines={1}>
        Last opened{' '}
        <Text style={{ color: p.text, fontWeight: '600' }}>
          {sura(recent.sura).tr} {recent.sura}:{recent.ayah}
        </Text>{' '}
        · {timeAgo(recent.at)}
      </Text>
      <Icon name="chevron-forward" size={18} color={p.textFaint} />
    </Tap>
  );
}

function NumberBadge({ n, active }: { n: number; active?: boolean }) {
  const p = usePalette();
  return (
    <View style={styles.badgeWrap}>
      <View
        style={[
          styles.badge,
          { borderColor: active ? p.gold : p.border, backgroundColor: active ? p.goldSoft : p.surface },
        ]}
      />
      <Text style={[styles.badgeText, { color: active ? p.gold : p.text }]}>{n}</Text>
    </View>
  );
}

function SuraRow({ s, lastRead }: { s: Sura; lastRead: Place | null }) {
  const p = usePalette();
  const here = lastRead?.sura === s.n;
  return (
    <Tap
      onPress={() => openVerse(here && lastRead ? lastRead : { sura: s.n, ayah: 1 })}
      style={[styles.row, { borderBottomColor: p.border }]}
    >
      <NumberBadge n={s.n} active={here} />
      <View style={{ flex: 1 }}>
        <Text style={[styles.rowTitle, { color: p.text }]}>{s.tr}</Text>
        <Text style={[styles.rowSub, { color: p.textMuted }]} numberOfLines={1}>
          {s.en} · {s.count} ayahs · {s.type === 'meccan' ? 'Makki' : 'Madani'}
        </Text>
        {here && lastRead && (
          <View style={styles.hereRow}>
            <Icon name="bookmark" size={12} color={p.gold} />
            <Text style={{ color: p.gold, fontSize: 12, fontWeight: '700' }}>
              Last read · ayah {lastRead.ayah}
            </Text>
          </View>
        )}
      </View>
      <Text style={[styles.rowArabic, { color: p.arabic, fontFamily: ARABIC_FONT }]}>{s.ar}</Text>
    </Tap>
  );
}

function JuzRow({ j, lastRead }: { j: Juz; lastRead: Place | null }) {
  const p = usePalette();
  const here = !!lastRead && juzOf(lastRead) === j.n;
  const opening = arabic(j.start.sura, j.start.ayah)
    .replace(/[\u06DE\u06E9]/g, '')
    .trim()
    .split(/\s+/)
    .slice(0, 3)
    .join(' ');
  return (
    <Tap onPress={() => openVerse(j.start)} style={[styles.row, { borderBottomColor: p.border }]}>
      <NumberBadge n={j.n} active={here} />
      <View style={{ flex: 1 }}>
        <Text style={[styles.rowTitle, { color: p.text }]}>Juz {j.n}</Text>
        <Text style={[styles.rowSub, { color: p.textMuted }]} numberOfLines={1}>
          {sura(j.start.sura).tr} {j.start.sura}:{j.start.ayah} · Page {j.page}
        </Text>
      </View>
      <Text
        numberOfLines={1}
        style={[styles.rowArabic, { color: p.arabic, fontFamily: ARABIC_FONT, fontSize: 18, maxWidth: '42%' }]}
      >
        {opening}
      </Text>
    </Tap>
  );
}

function TagRow({
  title,
  count,
  color,
  icon,
  onPress,
  onLongPress,
}: {
  title: string;
  count: number;
  color?: string;
  icon?: boolean;
  onPress: () => void;
  onLongPress?: () => void;
}) {
  const p = usePalette();
  return (
    <Tap
      onPress={onPress}
      onLongPress={onLongPress}
      style={[styles.tagRow, { backgroundColor: p.surface, borderColor: p.border }]}
    >
      <View style={[styles.tagIcon, { backgroundColor: (color ?? p.accent) + '22' }]}>
        {icon ? <Icon name="albums-outline" size={20} color={p.accent} /> : <TagDot color={color!} size={14} />}
      </View>
      <Text style={[styles.rowTitle, { color: p.text, flex: 1 }]} numberOfLines={1}>
        {title}
      </Text>
      <Text style={[styles.count, { color: p.textMuted, backgroundColor: p.surfaceAlt }]}>{count}</Text>
      <Icon name="chevron-forward" size={18} color={p.textFaint} />
    </Tap>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 16, width: '100%', maxWidth: 720, alignSelf: 'center' },
  topBar: { flexDirection: 'row', alignItems: 'center', gap: 8, marginBottom: 18 },
  greeting: { fontSize: 14, fontWeight: '500' },
  title: { fontSize: 30, fontWeight: '800', letterSpacing: -0.5 },
  card: { borderRadius: radius.xl, padding: 20 },
  cardRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  cardLabel: { fontSize: 12, fontWeight: '800', letterSpacing: 1.2, opacity: 0.85 },
  cardTitle: { fontSize: 26, fontWeight: '800', marginTop: 4 },
  cardHint: { fontSize: 15, lineHeight: 22, opacity: 0.9, marginTop: 6 },
  cardMeta: { fontSize: 14, opacity: 0.85, marginTop: 2 },
  cardArabic: { fontSize: 30, lineHeight: 56 },
  cardVerse: {
    fontSize: 19,
    lineHeight: 40,
    opacity: 0.9,
    writingDirection: 'rtl',
    textAlign: 'right',
    marginTop: 8,
  },
  progressTrack: { height: 6, borderRadius: 3, marginTop: 10, overflow: 'hidden' },
  progressFill: { height: 6, borderRadius: 3 },
  cardButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 999,
  },
  cardButtonText: { fontSize: 15, fontWeight: '700' },
  recent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 10,
    paddingHorizontal: 14,
    paddingVertical: 12,
    borderRadius: radius.md,
    borderWidth: 1,
  },
  filter: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingLeft: 14,
    borderRadius: radius.md,
    marginBottom: 6,
    minHeight: 46,
  },
  filterInput: { flex: 1, fontSize: 16, paddingVertical: 10 },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  rowTitle: { fontSize: 17, fontWeight: '700' },
  rowSub: { fontSize: 13.5, marginTop: 3 },
  rowArabic: { fontSize: 24, lineHeight: 44 },
  hereRow: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 4 },
  badgeWrap: { width: 42, height: 42, alignItems: 'center', justifyContent: 'center' },
  badge: {
    position: 'absolute',
    width: 32,
    height: 32,
    borderRadius: 9,
    borderWidth: 1.5,
    transform: [{ rotate: '45deg' }],
  },
  badgeText: { fontSize: 14, fontWeight: '700' },
  tagRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 14,
    padding: 14,
    borderRadius: radius.lg,
    borderWidth: 1,
    marginBottom: 10,
  },
  tagIcon: { width: 40, height: 40, borderRadius: 12, alignItems: 'center', justifyContent: 'center' },
  count: {
    fontSize: 13,
    fontWeight: '700',
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: 999,
    overflow: 'hidden',
  },
});
