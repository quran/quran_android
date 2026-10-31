import * as Clipboard from 'expo-clipboard';
import { router } from 'expo-router';
import { useMemo } from 'react';
import { Platform, Share, StyleSheet, Text, View } from 'react-native';
import {
  arabic,
  arabicNumber,
  formatRef,
  juzOf,
  pageOf,
  refKey,
  sura,
  translation,
  type Ref,
  type TranslationId,
} from '../data/quran';
import { actions, useStore } from '../state/store';
import { radius, usePalette } from '../theme';
import { TagChip } from './tags';
import { Icon, ListRow, Sheet, Tap, tick } from './ui';

export const ARABIC_FONT = 'AmiriQuran_400Regular';

export function openVerse(r: Ref) {
  router.push({ pathname: '/read/[sura]', params: { sura: String(r.sura), ayah: String(r.ayah) } });
}

export function verseShareText(r: Ref, translationId: TranslationId) {
  const t = translation(translationId, r.sura, r.ayah);
  return `${arabic(r.sura, r.ayah)}\n\n${t ? t + '\n\n' : ''}— ${formatRef(r)}`;
}

/** A saved verse / search result card that opens the reader on tap. */
export function VerseCard({
  verse,
  note,
  tagIds,
  highlight,
  onLongPress,
  footer,
}: {
  verse: Ref;
  note?: string;
  tagIds?: string[];
  highlight?: string;
  onLongPress?: () => void;
  footer?: React.ReactNode;
}) {
  const p = usePalette();
  const settings = useStore((s) => s.settings);
  const tags = useStore((s) => s.tags);
  const s = sura(verse.sura);
  const t = translation(settings.translation, verse.sura, verse.ayah);
  const verseTags = useMemo(
    () => (tagIds ? tags.filter((x) => tagIds.includes(x.id)) : []),
    [tags, tagIds]
  );

  return (
    <Tap
      onPress={() => openVerse(verse)}
      onLongPress={onLongPress}
      pressedOpacity={0.8}
      style={[styles.card, { backgroundColor: p.surface, borderColor: p.border }]}
    >
      <View style={styles.cardHead}>
        <View style={[styles.refPill, { backgroundColor: p.accentSoft }]}>
          <Text style={[styles.refText, { color: p.accent }]}>
            {verse.sura}:{verse.ayah}
          </Text>
        </View>
        <Text style={[styles.cardSura, { color: p.textMuted }]} numberOfLines={1}>
          {s.tr} · Page {pageOf(verse)}
        </Text>
        <Icon name="chevron-forward" size={18} color={p.textFaint} />
      </View>
      <Text
        style={[
          styles.cardArabic,
          {
            color: p.arabic,
            fontFamily: ARABIC_FONT,
            fontSize: Math.min(settings.arabicSize, 26),
            lineHeight: Math.min(settings.arabicSize, 26) * 2,
          },
        ]}
        numberOfLines={4}
      >
        {arabic(verse.sura, verse.ayah)}
      </Text>
      {t ? (
        <Text
          numberOfLines={5}
          style={[
            styles.cardTranslation,
            { color: p.text, writingDirection: settings.translation === 'ur' ? 'rtl' : 'ltr' },
            settings.translation === 'ur' && { textAlign: 'right' },
          ]}
        >
          {highlight ? <Highlighted text={t} query={highlight} color={p.goldSoft} /> : t}
        </Text>
      ) : null}
      {note ? (
        <View style={[styles.note, { backgroundColor: p.goldSoft }]}>
          <Icon name="create-outline" size={14} color={p.gold} />
          <Text style={[styles.noteText, { color: p.text }]}>{note}</Text>
        </View>
      ) : null}
      {verseTags.length > 0 && (
        <View style={styles.tagRow}>
          {verseTags.map((tag) => (
            <TagChip key={tag.id} tag={tag} compact />
          ))}
        </View>
      )}
      {footer}
    </Tap>
  );
}

function Highlighted({ text, query, color }: { text: string; query: string; color: string }) {
  const i = text.toLowerCase().indexOf(query.toLowerCase().trim());
  if (i < 0 || !query.trim()) return <>{text}</>;
  const j = i + query.trim().length;
  return (
    <>
      {text.slice(0, i)}
      <Text style={{ backgroundColor: color, fontWeight: '700' }}>{text.slice(i, j)}</Text>
      {text.slice(j)}
    </>
  );
}

/** What you can do with a verse in the reader. */
export function VerseActionsSheet({
  verse,
  onClose,
  onSaveToTag,
}: {
  verse: Ref | null;
  onClose: () => void;
  onSaveToTag: (r: Ref) => void;
}) {
  const p = usePalette();
  const lastRead = useStore((s) => s.lastRead);
  const saved = useStore((s) => s.saved);
  const tl = useStore((s) => s.settings.translation);
  if (!verse) return null;

  const isLastRead = lastRead?.sura === verse.sura && lastRead?.ayah === verse.ayah;
  const savedEntry = saved[refKey(verse)];
  const s = sura(verse.sura);

  const share = async () => {
    const text = verseShareText(verse, tl);
    onClose();
    if (Platform.OS === 'web') {
      await Clipboard.setStringAsync(text);
      return;
    }
    Share.share({ message: text }).catch(() => {});
  };

  return (
    <Sheet visible onClose={onClose}>
      <View style={styles.actionHead}>
        <Text style={[styles.actionTitle, { color: p.text }]}>
          {s.tr} · Ayah {verse.ayah}
        </Text>
        <Text style={[styles.actionSub, { color: p.textMuted }]}>
          Juz {juzOf(verse)} · Page {pageOf(verse)}
        </Text>
        <Text
          numberOfLines={2}
          style={{
            fontFamily: ARABIC_FONT,
            fontSize: 22,
            lineHeight: 44,
            color: p.arabic,
            writingDirection: 'rtl',
            textAlign: 'right',
            marginTop: 6,
          }}
        >
          {arabic(verse.sura, verse.ayah)} ﴿{arabicNumber(verse.ayah)}﴾
        </Text>
      </View>
      <ListRow
        icon={isLastRead ? 'bookmark' : 'bookmark-outline'}
        iconColor={p.gold}
        title={isLastRead ? 'This is your last read' : 'Mark as last read'}
        subtitle={
          isLastRead
            ? 'Tap to remove the marker'
            : 'The “Last read” card on the home screen will open here'
        }
        onPress={() => {
          tick();
          if (isLastRead) actions.clearLastRead();
          else actions.setLastRead(verse);
          onClose();
        }}
      />
      <ListRow
        icon={savedEntry ? 'pricetags' : 'pricetags-outline'}
        title={savedEntry ? 'Edit tags' : 'Save to a tag'}
        subtitle={
          savedEntry
            ? `In ${savedEntry.tagIds.length} tag${savedEntry.tagIds.length > 1 ? 's' : ''}`
            : 'Group verses under your own tags'
        }
        onPress={() => {
          onClose();
          onSaveToTag(verse);
        }}
      />
      <ListRow
        icon="copy-outline"
        title="Copy verse"
        subtitle="Arabic, translation and reference"
        onPress={async () => {
          await Clipboard.setStringAsync(verseShareText(verse, tl));
          tick();
          onClose();
        }}
      />
      {Platform.OS !== 'web' && <ListRow icon="share-outline" title="Share" onPress={share} />}
    </Sheet>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.lg, borderWidth: 1, padding: 16, gap: 10 },
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  refPill: { paddingHorizontal: 10, paddingVertical: 4, borderRadius: 999 },
  refText: { fontSize: 13, fontWeight: '700' },
  cardSura: { flex: 1, fontSize: 14, fontWeight: '500' },
  cardArabic: { writingDirection: 'rtl', textAlign: 'right' },
  cardTranslation: { fontSize: 15, lineHeight: 23 },
  note: { flexDirection: 'row', gap: 8, padding: 10, borderRadius: radius.sm, alignItems: 'flex-start' },
  noteText: { flex: 1, fontSize: 14, lineHeight: 20 },
  tagRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  actionHead: { paddingBottom: 8, marginBottom: 4 },
  actionTitle: { fontSize: 20, fontWeight: '700' },
  actionSub: { fontSize: 14, marginTop: 2 },
});
