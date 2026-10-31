import { useColorScheme } from 'react-native';
import { useStore } from './state/store';

export type ThemeName = 'light' | 'sepia' | 'dark';

export type Palette = {
  name: ThemeName;
  bg: string;
  surface: string;
  surfaceAlt: string;
  text: string;
  textMuted: string;
  textFaint: string;
  border: string;
  accent: string;
  accentSoft: string;
  onAccent: string;
  gold: string;
  goldSoft: string;
  arabic: string;
  danger: string;
  scrim: string;
  statusBar: 'light' | 'dark';
};

const light: Palette = {
  name: 'light',
  bg: '#F7F5F0',
  surface: '#FFFFFF',
  surfaceAlt: '#EFEBE3',
  text: '#1D2420',
  textMuted: '#5C6660',
  textFaint: '#97A09A',
  border: '#E3DED4',
  accent: '#1F6F5C',
  accentSoft: '#DCEDE6',
  onAccent: '#FFFFFF',
  gold: '#B8862F',
  goldSoft: '#F6EBD3',
  arabic: '#111712',
  danger: '#B3412E',
  scrim: 'rgba(10,15,12,0.45)',
  statusBar: 'dark',
};

const sepia: Palette = {
  name: 'sepia',
  bg: '#F3E9D6',
  surface: '#FBF4E6',
  surfaceAlt: '#EADDC4',
  text: '#3B2F22',
  textMuted: '#6F5E49',
  textFaint: '#A08D74',
  border: '#E0D0B3',
  accent: '#7A4E1E',
  accentSoft: '#EEDCBF',
  onAccent: '#FFF8EC',
  gold: '#A5701F',
  goldSoft: '#F0DDB8',
  arabic: '#2A2015',
  danger: '#A63A26',
  scrim: 'rgba(40,25,10,0.45)',
  statusBar: 'dark',
};

const dark: Palette = {
  name: 'dark',
  bg: '#0F1412',
  surface: '#171E1B',
  surfaceAlt: '#1F2824',
  text: '#E8EEEA',
  textMuted: '#A3B0A9',
  textFaint: '#6C7A73',
  border: '#27322D',
  accent: '#5CC3A4',
  accentSoft: '#1C3A31',
  onAccent: '#06231B',
  gold: '#E0B45C',
  goldSoft: '#3A3020',
  arabic: '#F2F5F3',
  danger: '#E2775F',
  scrim: 'rgba(0,0,0,0.6)',
  statusBar: 'light',
};

export const palettes: Record<ThemeName, Palette> = { light, sepia, dark };

/** Colors a user can give a tag. */
export const TAG_COLORS = [
  '#1F8A70', // emerald
  '#2F6FB3', // blue
  '#7B4FB8', // purple
  '#C2457A', // rose
  '#D9822B', // orange
  '#B8962F', // gold
  '#5B7A3A', // olive
  '#4A5560', // slate
];

export function usePalette(): Palette {
  const scheme = useColorScheme();
  const theme = useStore((s) => s.settings.theme);
  if (theme === 'system') return scheme === 'dark' ? dark : light;
  return palettes[theme];
}

export const radius = { sm: 10, md: 14, lg: 20, xl: 28 };
export const space = (n: number) => n * 4;
