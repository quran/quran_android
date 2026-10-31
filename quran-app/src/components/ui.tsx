import Ionicons from '@expo/vector-icons/Ionicons';
import * as Haptics from 'expo-haptics';
import type { ComponentProps, ReactNode } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type PressableProps,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { radius, usePalette } from '../theme';

export type IconName = ComponentProps<typeof Ionicons>['name'];

export function tick() {
  if (Platform.OS === 'web') return;
  Haptics.selectionAsync().catch(() => {});
}

export function Icon({ name, size = 22, color }: { name: IconName; size?: number; color?: string }) {
  const p = usePalette();
  return <Ionicons name={name} size={size} color={color ?? p.text} />;
}

export function Tap({
  style,
  pressedOpacity = 0.6,
  ...rest
}: PressableProps & { style?: StyleProp<ViewStyle>; pressedOpacity?: number }) {
  return (
    <Pressable
      {...rest}
      style={({ pressed }) => [style, pressed && { opacity: pressedOpacity }]}
    />
  );
}

export function IconButton({
  name,
  onPress,
  label,
  color,
  size = 22,
  filled,
}: {
  name: IconName;
  onPress: () => void;
  label: string;
  color?: string;
  size?: number;
  filled?: boolean;
}) {
  const p = usePalette();
  return (
    <Tap
      onPress={onPress}
      accessibilityLabel={label}
      accessibilityRole="button"
      hitSlop={8}
      style={[
        styles.iconButton,
        { backgroundColor: filled ? p.surfaceAlt : 'transparent' },
      ]}
    >
      <Icon name={name} size={size} color={color ?? p.text} />
    </Tap>
  );
}

export function Button({
  title,
  onPress,
  icon,
  kind = 'primary',
  style,
  disabled,
}: {
  title: string;
  onPress: () => void;
  icon?: IconName;
  kind?: 'primary' | 'soft' | 'ghost' | 'danger';
  style?: StyleProp<ViewStyle>;
  disabled?: boolean;
}) {
  const p = usePalette();
  const bg =
    kind === 'primary' ? p.accent : kind === 'soft' ? p.accentSoft : 'transparent';
  const fg =
    kind === 'primary' ? p.onAccent : kind === 'danger' ? p.danger : p.accent;
  return (
    <Tap
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      style={[
        styles.button,
        { backgroundColor: bg, opacity: disabled ? 0.4 : 1 },
        kind === 'danger' && { borderWidth: 1, borderColor: p.border },
        style,
      ]}
    >
      {icon && <Icon name={icon} size={18} color={fg} />}
      <Text style={[styles.buttonText, { color: fg }]}>{title}</Text>
    </Tap>
  );
}

export function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string; icon?: IconName }[];
  value: T;
  onChange: (v: T) => void;
}) {
  const p = usePalette();
  return (
    <View style={[styles.segmented, { backgroundColor: p.surfaceAlt }]}>
      {options.map((o) => {
        const active = o.value === value;
        return (
          <Tap
            key={o.value}
            onPress={() => {
              tick();
              onChange(o.value);
            }}
            accessibilityRole="tab"
            accessibilityState={{ selected: active }}
            style={[
              styles.segment,
              active && [styles.segmentActive, { backgroundColor: p.surface }],
            ]}
          >
            {o.icon && (
              <Icon name={o.icon} size={16} color={active ? p.accent : p.textMuted} />
            )}
            <Text
              style={[
                styles.segmentText,
                { color: active ? p.text : p.textMuted, fontWeight: active ? '700' : '500' },
              ]}
            >
              {o.label}
            </Text>
          </Tap>
        );
      })}
    </View>
  );
}

export function Sheet({
  visible,
  onClose,
  title,
  children,
  scroll = true,
}: {
  visible: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
  scroll?: boolean;
}) {
  const p = usePalette();
  const insets = useSafeAreaInsets();
  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={[StyleSheet.absoluteFill, { backgroundColor: p.scrim }]}>
          <Pressable style={{ flex: 1 }} onPress={onClose} accessibilityLabel="Close" />
        </View>
        <View style={{ flex: 1, justifyContent: 'flex-end' }} pointerEvents="box-none">
          <View
            style={[
              styles.sheet,
              { backgroundColor: p.surface, paddingBottom: Math.max(insets.bottom, 16) },
            ]}
          >
            <View style={[styles.grabber, { backgroundColor: p.border }]} />
            {title && <Text style={[styles.sheetTitle, { color: p.text }]}>{title}</Text>}
            {scroll ? (
              <ScrollView keyboardShouldPersistTaps="handled" bounces={false}>
                {children}
              </ScrollView>
            ) : (
              children
            )}
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}

export function ListRow({
  icon,
  iconColor,
  title,
  subtitle,
  onPress,
  right,
  destructive,
}: {
  icon?: IconName;
  iconColor?: string;
  title: string;
  subtitle?: string;
  onPress?: () => void;
  right?: ReactNode;
  destructive?: boolean;
}) {
  const p = usePalette();
  return (
    <Tap onPress={onPress} disabled={!onPress} style={styles.listRow}>
      {icon && (
        <View style={[styles.listIcon, { backgroundColor: p.surfaceAlt }]}>
          <Icon name={icon} size={20} color={destructive ? p.danger : iconColor ?? p.accent} />
        </View>
      )}
      <View style={{ flex: 1 }}>
        <Text style={[styles.listTitle, { color: destructive ? p.danger : p.text }]}>{title}</Text>
        {subtitle ? (
          <Text style={[styles.listSubtitle, { color: p.textMuted }]}>{subtitle}</Text>
        ) : null}
      </View>
      {right}
    </Tap>
  );
}

export function SectionLabel({ children, style }: { children: ReactNode; style?: StyleProp<TextStyle> }) {
  const p = usePalette();
  return <Text style={[styles.sectionLabel, { color: p.textFaint }, style]}>{children}</Text>;
}

export function EmptyState({
  icon,
  title,
  body,
  action,
}: {
  icon: IconName;
  title: string;
  body: string;
  action?: ReactNode;
}) {
  const p = usePalette();
  return (
    <View style={styles.empty}>
      <View style={[styles.emptyIcon, { backgroundColor: p.accentSoft }]}>
        <Icon name={icon} size={30} color={p.accent} />
      </View>
      <Text style={[styles.emptyTitle, { color: p.text }]}>{title}</Text>
      <Text style={[styles.emptyBody, { color: p.textMuted }]}>{body}</Text>
      {action}
    </View>
  );
}

const styles = StyleSheet.create({
  iconButton: {
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
  },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingHorizontal: 18,
    minHeight: 48,
    borderRadius: radius.md,
  },
  buttonText: { fontSize: 16, fontWeight: '600' },
  segmented: { flexDirection: 'row', borderRadius: radius.md, padding: 4 },
  segment: {
    flex: 1,
    flexDirection: 'row',
    gap: 6,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 10,
    borderRadius: radius.sm,
  },
  segmentActive: {
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  segmentText: { fontSize: 15 },
  sheet: {
    borderTopLeftRadius: radius.xl,
    borderTopRightRadius: radius.xl,
    paddingHorizontal: 20,
    paddingTop: 10,
    maxHeight: '88%',
    width: '100%',
    maxWidth: 640,
    alignSelf: 'center',
  },
  grabber: { width: 40, height: 5, borderRadius: 3, alignSelf: 'center', marginBottom: 12 },
  sheetTitle: { fontSize: 20, fontWeight: '700', marginBottom: 12 },
  listRow: { flexDirection: 'row', alignItems: 'center', gap: 14, paddingVertical: 12 },
  listIcon: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  listTitle: { fontSize: 16, fontWeight: '600' },
  listSubtitle: { fontSize: 13, marginTop: 2 },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '700',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    marginBottom: 8,
  },
  empty: { alignItems: 'center', paddingHorizontal: 32, paddingVertical: 48, gap: 10 },
  emptyIcon: {
    width: 64,
    height: 64,
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 6,
  },
  emptyTitle: { fontSize: 18, fontWeight: '700', textAlign: 'center' },
  emptyBody: { fontSize: 15, lineHeight: 22, textAlign: 'center', marginBottom: 8 },
});
