import { StyleSheet, Switch, Text, View } from 'react-native';
import { arabic, type TranslationId } from '../data/quran';
import { actions, useStore, type Settings } from '../state/store';
import { radius, usePalette } from '../theme';
import { IconButton, Segmented, Sheet } from './ui';
import { ARABIC_FONT } from './verse';

function Stepper({
  label,
  value,
  min,
  max,
  step,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (v: number) => void;
}) {
  const p = usePalette();
  return (
    <View style={styles.stepRow}>
      <Text style={[styles.stepLabel, { color: p.text }]}>{label}</Text>
      <IconButton
        name="remove"
        label={`Smaller ${label}`}
        filled
        onPress={() => onChange(Math.max(min, value - step))}
      />
      <Text style={[styles.stepValue, { color: p.text }]}>{value}</Text>
      <IconButton
        name="add"
        label={`Larger ${label}`}
        filled
        onPress={() => onChange(Math.min(max, value + step))}
      />
    </View>
  );
}

export function DisplaySheet({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  return (
    <Sheet visible={visible} onClose={onClose} title="Reading display">
      <DisplayOptions />
    </Sheet>
  );
}

/** Font sizes, translation, transliteration and theme. */
export function DisplayOptions() {
  const p = usePalette();
  const settings = useStore((s) => s.settings);
  const update = actions.updateSettings;
  return (
    <View>
      <Text
        style={{
          fontFamily: ARABIC_FONT,
          fontSize: settings.arabicSize,
          lineHeight: settings.arabicSize * 2,
          color: p.arabic,
          textAlign: 'center',
          backgroundColor: p.surfaceAlt,
          borderRadius: radius.md,
          paddingVertical: 6,
          marginBottom: 8,
        }}
      >
        {arabic(1, 2)}
      </Text>
      <Stepper label="Arabic size" value={settings.arabicSize} min={18} max={48} step={2} onChange={(v) => update({ arabicSize: v })} />
      <Stepper
        label="Translation size"
        value={settings.translationSize}
        min={12}
        max={26}
        step={1}
        onChange={(v) => update({ translationSize: v })}
      />
      <Text style={[styles.sheetLabel, { color: p.textMuted }]}>Translation</Text>
      <Segmented<TranslationId>
        value={settings.translation}
        onChange={(v) => update({ translation: v })}
        options={[
          { value: 'en', label: 'English' },
          { value: 'ur', label: 'اردو' },
          { value: 'none', label: 'None' },
        ]}
      />
      <View style={[styles.stepRow, { marginTop: 14 }]}>
        <Text style={[styles.stepLabel, { color: p.text }]}>Show transliteration</Text>
        <Switch
          value={settings.showTransliteration}
          onValueChange={(v) => update({ showTransliteration: v })}
          trackColor={{ true: p.accent, false: p.border }}
        />
      </View>
      <Text style={[styles.sheetLabel, { color: p.textMuted }]}>Theme</Text>
      <Segmented<Settings['theme']>
        value={settings.theme}
        onChange={(v) => update({ theme: v })}
        options={[
          { value: 'system', label: 'Auto' },
          { value: 'light', label: 'Light' },
          { value: 'sepia', label: 'Sepia' },
          { value: 'dark', label: 'Dark' },
        ]}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  stepRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 6 },
  stepLabel: { flex: 1, fontSize: 16, fontWeight: '600' },
  stepValue: { width: 32, textAlign: 'center', fontSize: 16, fontWeight: '700' },
  sheetLabel: { fontSize: 14, fontWeight: '600', marginTop: 16, marginBottom: 8 },
});
