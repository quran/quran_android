import { router, useLocalSearchParams } from 'expo-router';
import { useMemo, useState } from 'react';
import { FlatList, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { TagEditorSheet, TagPickerSheet } from '../../components/tags';
import { EmptyState, Icon, IconButton, Segmented, Tap } from '../../components/ui';
import { VerseCard } from '../../components/verse';
import type { Ref } from '../../data/quran';
import { actions, useStore } from '../../state/store';
import { usePalette } from '../../theme';

type Sort = 'recent' | 'order';

export default function TagScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const p = usePalette();
  const insets = useSafeAreaInsets();
  const tags = useStore((s) => s.tags);
  const saved = useStore((s) => s.saved);
  const [sort, setSort] = useState<Sort>('recent');
  const [editing, setEditing] = useState(false);
  const [retag, setRetag] = useState<Ref | null>(null);

  const isAll = id === 'all';
  const tag = tags.find((t) => t.id === id);

  const verses = useMemo(() => {
    const list = Object.values(saved).filter((v) => isAll || v.tagIds.includes(id));
    return list.sort((a, b) =>
      sort === 'recent' ? b.addedAt - a.addedAt : a.sura - b.sura || a.ayah - b.ayah
    );
  }, [saved, id, isAll, sort]);

  const back = () => (router.canGoBack() ? router.back() : router.replace('/'));

  if (!isAll && !tag) {
    return (
      <View style={{ flex: 1, backgroundColor: p.bg, paddingTop: insets.top }}>
        <IconButton name="chevron-back" label="Back" onPress={back} />
        <EmptyState icon="pricetag-outline" title="This tag no longer exists" body="It may have been deleted." />
      </View>
    );
  }

  const title = isAll ? 'All saved verses' : tag!.name;

  return (
    <View style={{ flex: 1, backgroundColor: p.bg }}>
      <FlatList
        data={verses}
        keyExtractor={(v) => `${v.sura}:${v.ayah}`}
        contentContainerStyle={[
          styles.content,
          { paddingTop: insets.top + 6, paddingBottom: insets.bottom + 32 },
        ]}
        ItemSeparatorComponent={() => <View style={{ height: 12 }} />}
        ListHeaderComponent={
          <View style={{ marginBottom: 16 }}>
            <View style={styles.topBar}>
              <IconButton name="chevron-back" label="Back" onPress={back} />
              <View style={{ flex: 1 }} />
              {!isAll && (
                <IconButton name="create-outline" label="Edit tag" filled onPress={() => setEditing(true)} />
              )}
            </View>
            <View style={styles.titleRow}>
              <View style={[styles.dot, { backgroundColor: tag?.color ?? p.accent }]} />
              <Text style={[styles.title, { color: p.text }]} numberOfLines={2}>
                {title}
              </Text>
            </View>
            <Text style={[styles.sub, { color: p.textMuted }]}>
              {verses.length} verse{verses.length === 1 ? '' : 's'} · tap to read, long-press to change tags
            </Text>
            {verses.length > 1 && (
              <View style={{ marginTop: 14 }}>
                <Segmented<Sort>
                  value={sort}
                  onChange={setSort}
                  options={[
                    { value: 'recent', label: 'Recently added', icon: 'time-outline' },
                    { value: 'order', label: 'Mushaf order', icon: 'book-outline' },
                  ]}
                />
              </View>
            )}
          </View>
        }
        ListEmptyComponent={
          <EmptyState
            icon="bookmarks-outline"
            title="No verses here yet"
            body="While reading, tap a verse and choose “Save to a tag”."
          />
        }
        renderItem={({ item }) => (
          <VerseCard
            verse={item}
            note={item.note}
            tagIds={isAll ? item.tagIds : item.tagIds.filter((t) => t !== id)}
            onLongPress={() => setRetag(item)}
            footer={
              !isAll ? (
                <Tap
                  onPress={() => actions.removeVerseFromTag(item, id)}
                  style={styles.remove}
                  accessibilityLabel="Remove from this tag"
                >
                  <Icon name="close-circle-outline" size={16} color={p.textFaint} />
                  <Text style={{ color: p.textFaint, fontSize: 13, fontWeight: '600' }}>
                    Remove from {tag!.name}
                  </Text>
                </Tap>
              ) : null
            }
          />
        )}
      />
      {tag && (
        <TagEditorSheet
          visible={editing}
          tag={tag}
          onClose={() => setEditing(false)}
          onDeleted={back}
        />
      )}
      <TagPickerSheet verse={retag} onClose={() => setRetag(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 16, width: '100%', maxWidth: 720, alignSelf: 'center' },
  topBar: { flexDirection: 'row', alignItems: 'center', marginLeft: -8, marginBottom: 8 },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  dot: { width: 16, height: 16, borderRadius: 8 },
  title: { fontSize: 28, fontWeight: '800', flex: 1, letterSpacing: -0.3 },
  sub: { fontSize: 14, marginTop: 6 },
  remove: { flexDirection: 'row', alignItems: 'center', gap: 6, alignSelf: 'flex-start', paddingTop: 2 },
});
