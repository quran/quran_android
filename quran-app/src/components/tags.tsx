import { useEffect, useMemo, useState } from 'react';
import { Alert, Platform, StyleSheet, Text, TextInput, View } from 'react-native';
import { formatRef, refKey, type Ref } from '../data/quran';
import { actions, useStore, type Tag } from '../state/store';
import { TAG_COLORS, radius, usePalette } from '../theme';
import { Button, Icon, Sheet, Tap, tick } from './ui';

export function TagDot({ color, size = 12 }: { color: string; size?: number }) {
  return <View style={{ width: size, height: size, borderRadius: size / 2, backgroundColor: color }} />;
}

export function TagChip({
  tag,
  selected,
  onPress,
  compact,
}: {
  tag: Tag;
  selected?: boolean;
  onPress?: () => void;
  compact?: boolean;
}) {
  const p = usePalette();
  return (
    <Tap
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole={onPress ? 'checkbox' : undefined}
      accessibilityState={onPress ? { checked: !!selected } : undefined}
      style={[
        styles.chip,
        compact && styles.chipCompact,
        {
          borderColor: selected ? tag.color : p.border,
          backgroundColor: selected ? tag.color + '22' : p.surface,
        },
      ]}
    >
      {selected && !compact ? (
        <Icon name="checkmark-circle" size={18} color={tag.color} />
      ) : (
        <TagDot color={tag.color} size={compact ? 8 : 10} />
      )}
      <Text
        numberOfLines={1}
        style={[styles.chipText, compact && { fontSize: 12 }, { color: p.text }]}
      >
        {tag.name}
      </Text>
    </Tap>
  );
}

function confirm(title: string, message: string, ok: string, onOk: () => void) {
  if (Platform.OS === 'web') {
    // eslint-disable-next-line no-alert
    if (globalThis.confirm?.(`${title}\n\n${message}`)) onOk();
    return;
  }
  Alert.alert(title, message, [
    { text: 'Cancel', style: 'cancel' },
    { text: ok, style: 'destructive', onPress: onOk },
  ]);
}

/** Create a new tag, or rename / recolor / delete an existing one. */
export function TagEditorSheet({
  visible,
  tag,
  onClose,
  onCreated,
  onDeleted,
}: {
  visible: boolean;
  tag?: Tag | null;
  onClose: () => void;
  onCreated?: (t: Tag) => void;
  onDeleted?: () => void;
}) {
  const p = usePalette();
  const tags = useStore((s) => s.tags);
  const [name, setName] = useState('');
  const [color, setColor] = useState(TAG_COLORS[0]);

  useEffect(() => {
    if (visible) {
      setName(tag?.name ?? '');
      setColor(tag?.color ?? TAG_COLORS[tags.length % TAG_COLORS.length]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, tag]);

  const trimmed = name.trim();
  const duplicate = tags.some(
    (t) => t.id !== tag?.id && t.name.toLowerCase() === trimmed.toLowerCase()
  );

  const save = () => {
    if (!trimmed || duplicate) return;
    if (tag) {
      actions.updateTag(tag.id, { name: trimmed, color });
    } else {
      onCreated?.(actions.createTag(trimmed, color));
    }
    tick();
    onClose();
  };

  return (
    <Sheet visible={visible} onClose={onClose} title={tag ? 'Edit tag' : 'New tag'}>
      <TextInput
        value={name}
        onChangeText={setName}
        placeholder="e.g. Patience, Duas, To memorise"
        placeholderTextColor={p.textFaint}
        autoFocus
        maxLength={40}
        onSubmitEditing={save}
        returnKeyType="done"
        style={[styles.input, { color: p.text, backgroundColor: p.surfaceAlt }]}
      />
      {duplicate && (
        <Text style={{ color: p.danger, marginTop: 6 }}>You already have a tag with this name.</Text>
      )}
      <Text style={[styles.label, { color: p.textMuted }]}>Colour</Text>
      <View style={styles.colors}>
        {TAG_COLORS.map((c) => (
          <Tap
            key={c}
            onPress={() => setColor(c)}
            accessibilityLabel={`Colour ${c}`}
            accessibilityState={{ selected: c === color }}
            style={[
              styles.swatch,
              { backgroundColor: c, borderColor: c === color ? p.text : 'transparent' },
            ]}
          >
            {c === color && <Icon name="checkmark" size={18} color="#fff" />}
          </Tap>
        ))}
      </View>
      <Button
        title={tag ? 'Save changes' : 'Create tag'}
        onPress={save}
        disabled={!trimmed || duplicate}
        style={{ marginTop: 20 }}
      />
      {tag && (
        <Button
          title="Delete tag"
          kind="danger"
          icon="trash-outline"
          style={{ marginTop: 10 }}
          onPress={() =>
            confirm(
              `Delete “${tag.name}”?`,
              'Verses saved only under this tag will be removed from your saved verses.',
              'Delete',
              () => {
                actions.deleteTag(tag.id);
                onClose();
                onDeleted?.();
              }
            )
          }
        />
      )}
    </Sheet>
  );
}

/** Pick which tags a verse belongs to. Tags can be created right here. */
export function TagPickerSheet({
  verse,
  onClose,
}: {
  verse: Ref | null;
  onClose: () => void;
}) {
  const p = usePalette();
  const tags = useStore((s) => s.tags);
  const saved = useStore((s) => s.saved);
  const [selected, setSelected] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);
  const [note, setNote] = useState('');

  const current = verse ? saved[refKey(verse)] : undefined;

  useEffect(() => {
    if (verse) {
      setSelected(current?.tagIds ?? []);
      setNote(current?.note ?? '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [verse]);

  const sorted = useMemo(() => [...tags].sort((a, b) => a.name.localeCompare(b.name)), [tags]);

  const toggle = (id: string) => {
    tick();
    setSelected((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  };

  const done = () => {
    if (!verse) return;
    actions.setVerseTags(verse, selected);
    if (selected.length) actions.setNote(verse, note);
    onClose();
  };

  return (
    <>
      <Sheet
        visible={!!verse && !creating}
        onClose={onClose}
        title={verse ? `Save ${formatRef(verse)}` : ''}
      >
        <Text style={[styles.label, { color: p.textMuted, marginTop: 0 }]}>
          {tags.length ? 'Choose one or more tags' : 'Create your first tag to start saving verses'}
        </Text>
        <View style={styles.chips}>
          {sorted.map((t) => (
            <TagChip key={t.id} tag={t} selected={selected.includes(t.id)} onPress={() => toggle(t.id)} />
          ))}
          <Tap
            onPress={() => setCreating(true)}
            style={[styles.chip, { borderColor: p.accent, borderStyle: 'dashed' }]}
          >
            <Icon name="add" size={18} color={p.accent} />
            <Text style={[styles.chipText, { color: p.accent }]}>New tag</Text>
          </Tap>
        </View>
        {selected.length > 0 && (
          <>
            <Text style={[styles.label, { color: p.textMuted }]}>Note (optional)</Text>
            <TextInput
              value={note}
              onChangeText={setNote}
              placeholder="Why this verse matters to you…"
              placeholderTextColor={p.textFaint}
              multiline
              style={[styles.input, styles.note, { color: p.text, backgroundColor: p.surfaceAlt }]}
            />
          </>
        )}
        <Button
          title={
            selected.length === 0
              ? current
                ? 'Remove from saved'
                : 'Done'
              : `Save to ${selected.length} tag${selected.length > 1 ? 's' : ''}`
          }
          kind={selected.length === 0 && current ? 'danger' : 'primary'}
          onPress={done}
          style={{ marginTop: 20 }}
        />
      </Sheet>
      <TagEditorSheet
        visible={!!verse && creating}
        onClose={() => setCreating(false)}
        onCreated={(t) => setSelected((s) => [...s, t.id])}
      />
    </>
  );
}

const styles = StyleSheet.create({
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: 999,
    borderWidth: 1.5,
    maxWidth: '100%',
  },
  chipCompact: { paddingHorizontal: 8, paddingVertical: 3, gap: 5, borderWidth: 1 },
  chipText: { fontSize: 15, fontWeight: '600', flexShrink: 1 },
  input: { fontSize: 17, paddingHorizontal: 16, paddingVertical: 14, borderRadius: radius.md },
  note: { minHeight: 80, textAlignVertical: 'top' },
  label: { fontSize: 14, fontWeight: '600', marginTop: 18, marginBottom: 10 },
  colors: { flexDirection: 'row', flexWrap: 'wrap', gap: 12 },
  swatch: {
    width: 40,
    height: 40,
    borderRadius: 20,
    borderWidth: 3,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
