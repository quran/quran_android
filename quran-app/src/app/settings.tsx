import * as Clipboard from 'expo-clipboard';
import { router } from 'expo-router';
import { useState } from 'react';
import { Platform, ScrollView, Share, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { DisplayOptions } from '../components/display';
import { Button, IconButton, ListRow, SectionLabel, Sheet } from '../components/ui';
import { formatRef } from '../data/quran';
import { actions, useStore } from '../state/store';
import { radius, usePalette } from '../theme';

export default function SettingsScreen() {
  const p = usePalette();
  const insets = useSafeAreaInsets();
  const tags = useStore((s) => s.tags);
  const saved = useStore((s) => s.saved);
  const lastRead = useStore((s) => s.lastRead);
  const [importOpen, setImportOpen] = useState(false);
  const [importText, setImportText] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  const savedCount = Object.keys(saved).length;

  const exportBackup = async () => {
    const json = actions.exportBackup();
    if (Platform.OS === 'web') {
      await Clipboard.setStringAsync(json);
      setMessage('Backup copied to clipboard. Paste it somewhere safe.');
      return;
    }
    try {
      await Share.share({ message: json, title: 'Quran app backup' });
    } catch {
      await Clipboard.setStringAsync(json);
      setMessage('Backup copied to clipboard.');
    }
  };

  const runImport = () => {
    if (actions.importBackup(importText)) {
      setImportOpen(false);
      setImportText('');
      setMessage('Backup restored.');
    } else {
      setMessage('That doesn’t look like a backup from this app.');
    }
  };

  return (
    <View style={{ flex: 1, backgroundColor: p.bg }}>
      <ScrollView
        contentContainerStyle={[
          styles.content,
          { paddingTop: insets.top + 6, paddingBottom: insets.bottom + 32 },
        ]}
      >
        <View style={styles.topBar}>
          <IconButton
            name="chevron-back"
            label="Back"
            onPress={() => (router.canGoBack() ? router.back() : router.replace('/'))}
          />
        </View>
        <Text style={[styles.title, { color: p.text }]}>Settings</Text>

        <SectionLabel style={styles.section}>Reading</SectionLabel>
        <View style={[styles.card, { backgroundColor: p.surface, borderColor: p.border }]}>
          <DisplayOptions />
        </View>

        <SectionLabel style={styles.section}>Your place</SectionLabel>
        <View style={[styles.card, { backgroundColor: p.surface, borderColor: p.border }]}>
          <ListRow
            icon="bookmark"
            iconColor={p.gold}
            title={lastRead ? formatRef(lastRead) : 'No last-read marker set'}
            subtitle={lastRead ? 'Tap to clear the marker' : 'Set it from any verse while reading'}
            onPress={lastRead ? actions.clearLastRead : undefined}
          />
        </View>

        <SectionLabel style={styles.section}>Backup</SectionLabel>
        <View style={[styles.card, { backgroundColor: p.surface, borderColor: p.border }]}>
          <Text style={{ color: p.textMuted, fontSize: 14, lineHeight: 20, marginBottom: 4 }}>
            Everything is stored only on this phone: {tags.length} tag{tags.length === 1 ? '' : 's'},{' '}
            {savedCount} saved verse{savedCount === 1 ? '' : 's'}. Export a backup before reinstalling.
          </Text>
          <ListRow icon="cloud-upload-outline" title="Export backup" subtitle="Share or copy as text" onPress={exportBackup} />
          <ListRow
            icon="cloud-download-outline"
            title="Restore from backup"
            subtitle="Replaces your current tags and saved verses"
            onPress={() => setImportOpen(true)}
          />
          {message && <Text style={{ color: p.accent, fontWeight: '600', marginTop: 4 }}>{message}</Text>}
        </View>

        <SectionLabel style={styles.section}>About</SectionLabel>
        <View style={[styles.card, { backgroundColor: p.surface, borderColor: p.border }]}>
          <Text style={{ color: p.textMuted, fontSize: 13.5, lineHeight: 20 }}>
            Uthmani text and translations (Saheeh International, Urdu) from The Noble Qur’an Encyclopedia
            (quranenc.com) via the quran-json project, CC BY-SA 4.0. Transliteration from tanzil.net. Page
            and juz numbers follow the Madani mushaf. Arabic font: Amiri Quran.
          </Text>
        </View>
      </ScrollView>

      <Sheet visible={importOpen} onClose={() => setImportOpen(false)} title="Restore from backup">
        <Text style={{ color: p.textMuted, marginBottom: 10 }}>Paste the backup text you exported earlier.</Text>
        <TextInput
          value={importText}
          onChangeText={setImportText}
          multiline
          placeholder="{ … }"
          placeholderTextColor={p.textFaint}
          style={[styles.importInput, { color: p.text, backgroundColor: p.surfaceAlt }]}
        />
        <Button title="Restore" icon="refresh" onPress={runImport} disabled={!importText.trim()} style={{ marginTop: 14 }} />
      </Sheet>
    </View>
  );
}

const styles = StyleSheet.create({
  content: { paddingHorizontal: 16, width: '100%', maxWidth: 720, alignSelf: 'center' },
  topBar: { flexDirection: 'row', marginLeft: -8 },
  title: { fontSize: 30, fontWeight: '800', marginTop: 4, letterSpacing: -0.4 },
  section: { marginTop: 24 },
  card: { borderRadius: radius.lg, borderWidth: 1, padding: 16 },
  importInput: {
    minHeight: 160,
    maxHeight: 280,
    borderRadius: radius.md,
    padding: 12,
    fontSize: 13,
    textAlignVertical: 'top',
    fontFamily: Platform.select({ ios: 'Menlo', default: 'monospace' }),
  },
});
